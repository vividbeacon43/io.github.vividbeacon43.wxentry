package com.zafiro.wxentry;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.value.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;
import com.android.tools.smali.dexlib2.immutable.value.ImmutableStringEncodedValue;
import com.android.tools.smali.dexlib2.rewriter.*;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;

/** Rebuild all DEX indexes after namespace replacement; input remains signed and unchanged. */
public final class DexRewriteEngine {
    private DexRewriteEngine() {}
    public static byte[] rewrite(byte[] input,final String namespace) throws Exception {
        final String slash=namespace.replace('.','/');
        DexBackedDexFile original=new DexBackedDexFile(null,input);
        DexRewriter rewriter=new DexRewriter(new RewriterModule() {
            public Rewriter<String> getTypeRewriter(Rewriters rewrites) {
                return new Rewriter<String>() { public String rewrite(String value) { return map(value,namespace,slash); } };
            }
            public Rewriter<EncodedValue> getEncodedValueRewriter(Rewriters rewrites) {
                return new EncodedValueRewriter(rewrites) {
                    public EncodedValue rewrite(EncodedValue value) {
                        if(value instanceof StringEncodedValue)return new ImmutableStringEncodedValue(map(((StringEncodedValue)value).getValue(),namespace,slash));
                        return super.rewrite(value);
                    }
                };
            }
            public Rewriter<Instruction> getInstructionRewriter(Rewriters rewrites) {
                return new InstructionRewriter(rewrites) {
                    public Instruction rewrite(Instruction ins) {
                        if(ins instanceof ReferenceInstruction&&((ReferenceInstruction)ins).getReference() instanceof StringReference) {
                            StringReference ref=(StringReference)((ReferenceInstruction)ins).getReference();
                            String value=map(ref.getString(),namespace,slash);
                            if(!value.equals(ref.getString())) {
                                int register=((OneRegisterInstruction)ins).getRegisterA();
                                if(ins.getOpcode()==Opcode.CONST_STRING)return new ImmutableInstruction21c(ins.getOpcode(),register,new ImmutableStringReference(value));
                                if(ins.getOpcode()==Opcode.CONST_STRING_JUMBO)return new ImmutableInstruction31c(ins.getOpcode(),register,new ImmutableStringReference(value));
                            }
                        }
                        return super.rewrite(ins);
                    }
                };
            }
        });
        DexFile transformed=rewriter.getDexFileRewriter().rewrite(original);
        MemoryDataStore store=new MemoryDataStore(input.length);
        try { DexPool.writeTo(store,transformed);return store.getData(); }
        finally { store.close(); }
    }
    static String map(String value,String dot,String slash) {
        return value.replace("de.robv.android.xposed.",dot).replace("de/robv/android/xposed/",slash);
    }
}
