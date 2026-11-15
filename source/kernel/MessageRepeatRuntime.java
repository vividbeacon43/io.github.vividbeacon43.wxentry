package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.WeakHashMap;

/** Adds a one-shot resend action to the native chat message menu. */
final class MessageRepeatRuntime {
    private static final int ACTION_ID = 0x57585201;
    private static final int POPUP_COPY = 0;
    private static final int ADAPTER_COPY = 102;
    private static final int ADAPTER_COPY_ALTERNATE = 141;
    private static final int TEXT_TYPE = 1;
    private static final long MENU_TIMEOUT_MS = 120_000L;
    private static final String ITEMS = "com.tencent.mm.ui.chatting.viewitems.";
    private static boolean started;
    private static volatile boolean ready;

    private final Map<MenuItem, Selection> selections = new WeakHashMap<>();
    private final Method addItem;
    private final Method messageForTag;
    private final Method activePeer;
    private final Method readOnly;
    private final Method chatActivity;
    private final Method messageId;
    private final Method type;
    private final Method talker;
    private final Method senderDirection;
    private final Method body;
    private final Method stripGroupSender;
    private final Method sendType;
    private final Method send;
    private Method sendMedia;
    private Method mediaFile;
    private Method voiceIsLoaded;
    private Method voiceSize;
    private Method voiceInfo;
    private Field voiceDuration;
    private final Field bubble;
    private final Field wrapper;
    private final Field wrappedMessage;

    private MessageRepeatRuntime(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> menu = load(loader, "ak5.h4");
        Class<?> chat = load(loader, "wk5.d");
        Class<?> message = load(loader, "com.tencent.mm.storage.f9");
        addItem = method(menu, "c", MenuItem.class,
                int.class, int.class, int.class, CharSequence.class, int.class);
        messageForTag = method(load(loader, ITEMS + "ws"), "c", message);
        activePeer = method(chat, "x", String.class);
        readOnly = method(chat, "I", boolean.class);
        chatActivity = method(chat, "g", Activity.class);
        messageId = method(message, "getMsgId", long.class);
        type = method(message, "getType", int.class);
        talker = method(message, "O0", String.class);
        senderDirection = method(message, "A0", int.class);
        body = method(message, "j", String.class);
        stripGroupSender = staticMethod(load(loader, "w41.ba"), "w", String.class, String.class);
        sendType = staticMethod(load(loader, "w41.e2"), "C", int.class, String.class);
        send = staticMethod(load(loader, "di0.c"), "a", void.class,
                String.class, String.class, int.class, int.class, Map.class, String.class, String.class);
        bubble = XposedHelpers.findField(load(loader, ITEMS + "yo"), "b");
        if (!View.class.isAssignableFrom(bubble.getType())) throw new NoSuchFieldException("text bubble");
        Class<?> model = load(loader, "qm5.d");
        wrapper = field(model, "d", load(loader, "xn5.a"));
        wrappedMessage = field(wrapper.getType(), "b", message);
    }

    static synchronized void start(ClassLoader loader) throws Throwable {
        if (started) return;
        MessageRepeatRuntime runtime = new MessageRepeatRuntime(loader);
        started = true;
        runtime.hookDirection(loader, "eo", "jo", "ko");
        runtime.hookDirection(loader, "lo", "qo", "ro");
        runtime.hookPopupClicks(loader);
        try {
            runtime.hookMediaMenu(loader);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            logFailure("media menu setup", unavailable);
        }
        ready = true;
        Kernel.T("ARREPEAT ready native-click");
    }

    static boolean available() {
        return ready;
    }

    private static boolean enabled() {
        return ready && !Kernel.MODULE_OFF && !Kernel.f(Kernel.MESSAGE_REPEAT_OFF_PATH);
    }

    private void hookMediaMenu(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> messageClass = wrappedMessage.getType();
        Method media = staticMethod(load(loader, "com.tencent.mm.ui.chatting.x3"), "h", void.class,
                Context.class, String.class, messageClass, boolean.class, boolean.class, String.class);
        Class<?> voiceInfoClass = load(loader, "q71.d1");
        mediaFile = method(messageClass, "z0", String.class);
        voiceIsLoaded = staticMethod(load(loader, ITEMS + "sq"), "p", boolean.class, messageClass);
        voiceSize = staticMethod(load(loader, "q71.c1"), "g", int.class, messageClass, String.class);
        voiceInfo = staticMethod(load(loader, "q71.e1"), "k", voiceInfoClass, String.class);
        voiceDuration = field(voiceInfoClass, "l", int.class);
        Class<?> creator = load(loader, ITEMS + "o0");
        Class<?> selector = load(loader, ITEMS + "r0");
        Field creatorChat = field(creator, "a", activePeer.getDeclaringClass());
        Method create = method(creator, "a", void.class, addItem.getDeclaringClass(),
                View.class, ContextMenu.ContextMenuInfo.class);
        Method select = method(selector, "onMMMenuItemSelected", void.class, MenuItem.class, int.class);
        XposedBridge.hookMethod(select, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!isRepeatItem(p.args[0])) return;
                p.setResult(null);
                try { activatePopupItem((MenuItem) p.args[0]); }
                catch (ReflectiveOperationException failure) { logFailure("media selection", failure); }
            }
        });
        XposedBridge.hookMethod(create, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!canAdd(p) || !(p.args[1] instanceof View)) return;
                try {
                    View anchor = (View) p.args[1];
                    Object tag = anchor.getTag();
                    if (!messageForTag.getDeclaringClass().isInstance(tag)) return;
                    Object message = messageForTag.invoke(tag);
                    if (message == null || !isMedia((Integer) type.invoke(message))) return;
                    Menu menu = (Menu) p.args[0];
                    if (menu.size() > 0)
                        add(menu, menu.getItem(0), anchor, creatorChat.get(p.thisObject), message);
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    logFailure("media menu", failure);
                }
            }
        });
        sendMedia = media;
    }

    private void hookDirection(ClassLoader loader, String layout, String createName, String selectName)
            throws ReflectiveOperationException {
        Class<?> itemClass = load(loader, ITEMS + layout);
        Class<?> menuClass = load(loader, "ak5.h4");
        Class<?> chatClass = load(loader, "wk5.d");
        Class<?> msgClass = load(loader, "com.tencent.mm.storage.f9");
        Class<?> modelClass = wrapper.getDeclaringClass();
        Class<?> creator = load(loader, ITEMS + createName);
        Class<?> selector = load(loader, ITEMS + selectName);
        Field itemChat = field(itemClass, "s", chatClass);
        Field creatorItem = field(creator, "e", itemClass);
        Field creatorHolder = field(creator, "b", bubble.getDeclaringClass());
        Field selectorItem = field(selector, "g", itemClass);
        Field selectorMessage = field(selector, "f", msgClass);
        Method create = method(creator, "a", void.class, menuClass, View.class, ContextMenu.ContextMenuInfo.class);
        Method select = method(selector, "onMMMenuItemSelected", void.class, MenuItem.class, int.class);
        Method adapterCreate = method(itemClass, "S", boolean.class, menuClass, View.class, modelClass);
        Method adapterSelect = method(itemClass, "R", boolean.class, MenuItem.class, chatClass, modelClass);

        XposedBridge.hookMethod(create, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!canAdd(p)) return;
                try {
                    View text = (View) bubble.get(creatorHolder.get(p.thisObject));
                    Object tag = text.getTag();
                    if (!messageForTag.getDeclaringClass().isInstance(tag)) return;
                    add((Menu) p.args[0], POPUP_COPY, text, itemChat.get(creatorItem.get(p.thisObject)),
                            messageForTag.invoke(tag));
                } catch (Throwable failure) {
                    logFailure("menu", failure);
                }
            }
        });
        XposedBridge.hookMethod(select, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!isRepeatItem(p.args[0])) return;
                p.setResult(null);
                Selection selection = take((MenuItem) p.args[0]);
                if (selection == null) return;
                try {
                    repeat(selection, itemChat.get(selectorItem.get(p.thisObject)),
                            selectorMessage.get(p.thisObject));
                } catch (Throwable failure) {
                    logFailure("selection", failure);
                }
            }
        });
        XposedBridge.hookMethod(adapterCreate, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!canAdd(p) || !(p.args[1] instanceof View)) return;
                try {
                    Menu menu = (Menu) p.args[0];
                    int copyId = menu.findItem(ADAPTER_COPY) != null ? ADAPTER_COPY : ADAPTER_COPY_ALTERNATE;
                    add(menu, copyId, (View) p.args[1], itemChat.get(p.thisObject),
                            messageFromModel(p.args[2]));
                } catch (Throwable failure) {
                    logFailure("adapter menu", failure);
                }
            }
        });
        XposedBridge.hookMethod(adapterSelect, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!isRepeatItem(p.args[0])) return;
                p.setResult(Boolean.TRUE);
                Selection selection = take((MenuItem) p.args[0]);
                if (selection == null) return;
                try {
                    repeat(selection, p.args[1], messageFromModel(p.args[2]));
                } catch (Throwable failure) {
                    logFailure("adapter selection", failure);
                }
            }
        });
    }

    private void hookPopupClicks(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> popup = load(loader, "zu5.s0");
        Field popupMenu = field(popup, "x", load(loader, "ak5.h4"));
        Method render = method(popup, "p", boolean.class, int.class, int.class);
        Method dismiss = method(popup, "a", boolean.class);
        Field[] windows = new Field[4];
        String[] windowNames = {"i", "m", "n", "o"};
        for (int i = 0; i < windows.length; i++) {
            windows[i] = XposedHelpers.findField(popup, windowNames[i]);
            if (!PopupWindow.class.isAssignableFrom(windows[i].getType()))
                throw new NoSuchFieldException(windowNames[i]);
        }
        XposedBridge.hookMethod(render, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!ready || p.hasThrowable()) return;
                try {
                    Menu menu = (Menu) popupMenu.get(p.thisObject);
                    MenuItem item = menu == null ? null : menu.findItem(ACTION_ID);
                    if (item == null) return;
                    Selection selection;
                    synchronized (selections) {
                        selection = selections.get(item);
                    }
                    if (selection == null) return;
                    for (Field field : windows) {
                        PopupWindow window = (PopupWindow) field.get(p.thisObject);
                        if (window == null || !window.isShowing()) continue;
                        View button = repeatButton(window.getContentView());
                        if (button == null) continue;
                        Object current = p.thisObject;
                        button.setOnClickListener(view -> {
                            try {
                                Kernel.T("ARREPEAT native-view-click");
                                if (take(item) != null)
                                    repeat(selection, selection.chat.get(), selection.message.get());
                            } catch (Throwable failure) {
                                logFailure("popup view click", failure);
                            } finally {
                                try { dismiss.invoke(current); }
                                catch (Throwable failure) { logFailure("popup dismiss", failure); }
                            }
                        });
                        Kernel.T("ARREPEAT native-view-bound");
                    }
                } catch (Throwable failure) {
                    logFailure("popup view binding", failure);
                }
            }
        });
        Field hasNoHeader = field(popup, "H", boolean.class);
        Class<?> listClick = load(loader, "zu5.j0");
        Field listPopup = field(listClick, "d", popup);
        Method onItemClick = method(listClick, "onItemClick", void.class,
                AdapterView.class, View.class, int.class, long.class);

        XposedBridge.hookMethod(onItemClick, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!ready) return;
                try {
                    Object current = listPopup.get(p.thisObject);
                    int position = (Integer) p.args[2];
                    if (!hasNoHeader.getBoolean(current) && position >= 1) position--;
                    clickPopup((Menu) popupMenu.get(current), position);
                } catch (Throwable failure) {
                    logFailure("popup list click", failure);
                }
            }
        });
        for (String name : new String[]{"zu5.b0", "zu5.k0", "zu5.l0", "zu5.m0"}) {
            Class<?> listener = load(loader, name);
            Field listenerPopup = field(listener, "e", popup);
            Field listenerPosition = field(listener, "d", int.class);
            Method onClick = method(listener, "onClick", void.class, View.class);
            XposedBridge.hookMethod(onClick, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!ready) return;
                    try {
                        Object current = listenerPopup.get(p.thisObject);
                        clickPopup((Menu) popupMenu.get(current), listenerPosition.getInt(p.thisObject));
                    } catch (Throwable failure) {
                        logFailure("popup click", failure);
                    }
                }
            });
        }
    }

    private void clickPopup(Menu menu, int position) throws ReflectiveOperationException {
        if (menu == null || position < 0 || position >= menu.size()) return;
        activatePopupItem(menu.getItem(position));
    }

    private void activatePopupItem(MenuItem item) throws ReflectiveOperationException {
        if (!isRepeatItem(item)) return;
        Selection selection = take(item);
        if (selection == null) return;
        Kernel.T("ARREPEAT native-click");
        repeat(selection, selection.chat.get(), selection.message.get());
    }

    private static View repeatButton(View view) {
        if (view instanceof TextView && "复读".contentEquals(((TextView) view).getText())) {
            for (View current = view; current != null; ) {
                if (current.isClickable()) return current;
                current = current.getParent() instanceof View ? (View) current.getParent() : null;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View button = repeatButton(group.getChildAt(i));
                if (button != null) return button;
            }
        }
        return null;
    }

    private boolean canAdd(XC_MethodHook.MethodHookParam p) {
        return enabled() && !p.hasThrowable() && p.args[0] instanceof Menu;
    }

    private Object messageFromModel(Object model) throws IllegalAccessException {
        Object value = model == null ? null : wrapper.get(model);
        return value == null ? null : wrappedMessage.get(value);
    }

    private void add(Menu menu, int copyId, View anchor, Object chat, Object message)
            throws ReflectiveOperationException {
        MenuItem copy = menu.findItem(copyId);
        if (copy == null || !copy.isVisible() || !copy.isEnabled()
                || message == null || (Integer) type.invoke(message) != TEXT_TYPE) return;
        add(menu, copy, anchor, chat, message);
    }

    private void add(Menu menu, MenuItem nativeItem, View anchor, Object chat, Object message)
            throws ReflectiveOperationException {
        if (menu.findItem(ACTION_ID) != null) return;
        String owner = account();
        if (owner.isEmpty()) return;
        String peer = peerFor(chat, message);
        if (peer.isEmpty()) return;
        int kind = (Integer) type.invoke(message);
        if (kind == TEXT_TYPE && textFor(message, peer).trim().isEmpty()) return;
        if (kind != TEXT_TYPE && sendMedia == null) return;
        int icon = anchor.getResources().getIdentifier("icons_outlined_refresh", "raw", "com.tencent.mm");
        if (icon == 0) return;
        MenuItem item = (MenuItem) addItem.invoke(menu, nativeItem.getGroupId(), ACTION_ID, 0, "复读", icon);
        if (item == null) return;
        synchronized (selections) {
            selections.put(item, new Selection(owner, peer, (Long) messageId.invoke(message), kind, chat, message));
        }
        Kernel.T("ARREPEAT menu-added type=" + kind);
    }

    private static boolean isRepeatItem(Object candidate) {
        return candidate instanceof MenuItem && ((MenuItem) candidate).getItemId() == ACTION_ID;
    }

    private Selection take(MenuItem item) {
        synchronized (selections) {
            return selections.remove(item);
        }
    }

    private void repeat(Selection selection, Object chat, Object message) throws ReflectiveOperationException {
        Activity activity = chat == null ? null : (Activity) chatActivity.invoke(chat);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            String peer = peerFor(chat, message);
            if (!enabled() || !selection.owner.equals(account()) || !selection.peer.equals(peer)
                    || selection.messageId != (Long) messageId.invoke(message)
                    || selection.kind != (Integer) type.invoke(message)
                    || SystemClock.elapsedRealtime() - selection.createdAt > MENU_TIMEOUT_MS) {
                Toast.makeText(activity, "菜单已失效，请重新长按消息", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selection.kind != TEXT_TYPE) {
                if (selection.kind == 34) {
                    if (!repeatVoice(message, peer)) {
                        Toast.makeText(activity, "请先播放这条语音，加载后再复读", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else {
                    sendMedia.invoke(null, activity, peer, message, peer.endsWith("@chatroom"), false, "");
                }
                Kernel.T("ARREPEAT send-requested type=" + selection.kind);
                return;
            }
            String text = textFor(message, peer);
            if (text.trim().isEmpty()) {
                Toast.makeText(activity, "这条消息暂时无法复读", Toast.LENGTH_SHORT).show();
                return;
            }
            // Invoke the native plain-text send task, without forwarding or running chat commands.
            send.invoke(null, peer, text, TEXT_TYPE, 0, null, null, null);
            Kernel.T("ARREPEAT send-requested");
        } catch (Throwable failure) {
            logFailure("send", failure);
            Toast.makeText(activity, "暂时无法复读，请稍后再试", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean repeatVoice(Object message, String peer) throws Throwable {
        String file = (String) mediaFile.invoke(message);
        if (file == null || file.isEmpty() || !Boolean.TRUE.equals(voiceIsLoaded.invoke(null, message))
                || (Integer) voiceSize.invoke(null, message, file) <= 0) return false;
        Object info = voiceInfo.invoke(null, file);
        int duration = info == null ? 0 : voiceDuration.getInt(info);
        if (duration <= 0) return false;
        VoiceForwardSender.send(message, peer, duration);
        return true;
    }

    private String peerFor(Object chat, Object message) throws ReflectiveOperationException {
        if (chat == null || message == null || Boolean.TRUE.equals(readOnly.invoke(chat))
                || (Long) messageId.invoke(message) <= 0) return "";
        int kind = (Integer) type.invoke(message);
        if (kind != TEXT_TYPE && !isMedia(kind)) return "";
        String peer = (String) activePeer.invoke(chat);
        return peer != null && peer.indexOf(',') < 0 && peer.equals(talker.invoke(message))
                && (Integer) sendType.invoke(null, peer) == TEXT_TYPE ? peer : "";
    }

    private static boolean isMedia(int kind) {
        switch (kind) {
            case 3: case 13: case 23: case 33: case 39: // Image variants recognized by f9.J2().
            case 34: // Voice
            case 43: case 62: // Video and short video
            case 47: // Sticker
                return true;
            default:
                return false;
        }
    }

    private String textFor(Object message, String peer) throws ReflectiveOperationException {
        String text = (String) body.invoke(message);
        if (text != null && text.indexOf(':') >= 0 && text.startsWith(":\n", text.indexOf(':'))
                && peer.endsWith("@chatroom") && (Integer) senderDirection.invoke(message) == 0) {
            text = (String) stripGroupSender.invoke(null, text);
        }
        return text == null ? "" : text;
    }

    private static String account() {
        String owner = Kernel.receiptAccount();
        return owner == null || "null".equals(owner) ? "" : owner;
    }

    private static Class<?> load(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    private static Method method(Class<?> owner, String name, Class<?> result, Class<?>... args)
            throws NoSuchMethodException {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            Method resolved = XposedHelpers.findMethodExactIfExists(current, name, (Object[]) args);
            if (resolved == null) continue;
            if (resolved.getReturnType() != result) break;
            resolved.setAccessible(true);
            return resolved;
        }
        throw new NoSuchMethodException(owner.getName() + "." + name);
    }

    private static Method staticMethod(Class<?> owner, String name, Class<?> result, Class<?>... args)
            throws NoSuchMethodException {
        Method resolved = method(owner, name, result, args);
        if (!Modifier.isStatic(resolved.getModifiers())) throw new NoSuchMethodException(owner.getName() + "." + name);
        return resolved;
    }

    private static Field field(Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
        Field resolved = XposedHelpers.findField(owner, name);
        if (resolved.getType() != type) throw new NoSuchFieldException(owner.getName() + "." + name);
        return resolved;
    }

    private static void logFailure(String stage, Throwable failure) {
        Kernel.T("ARREPEAT " + stage + " unavailable " + failure.getClass().getSimpleName());
    }

    private static final class Selection {
        final String owner;
        final String peer;
        final long messageId;
        final int kind;
        final WeakReference<Object> chat;
        final WeakReference<Object> message;
        final long createdAt = SystemClock.elapsedRealtime();

        Selection(String owner, String peer, long messageId, int kind, Object chat, Object message) {
            this.owner = owner;
            this.peer = peer;
            this.messageId = messageId;
            this.kind = kind;
            this.chat = new WeakReference<>(chat);
            this.message = new WeakReference<>(message);
        }
    }
}
