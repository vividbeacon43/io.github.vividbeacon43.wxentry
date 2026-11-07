package ak5;
public class h4 implements android.view.Menu {
    public java.util.List<android.view.MenuItem> items=new java.util.ArrayList<>();
    public int icons;
    public android.view.MenuItem findItem(int id) { for(android.view.MenuItem i:items)if(i.getItemId()==id)return i;return null; }
    public android.view.MenuItem getItem(int i) { return items.get(i); }
    public int size() { return items.size(); }
    public android.view.MenuItem add(final int group,final int id,int order,CharSequence label) {
        android.view.MenuItem item=new android.view.MenuItem() { public int getGroupId(){return group;} public int getItemId(){return id;} public android.view.MenuItem setIcon(android.graphics.drawable.Drawable icon){icons++;return this;} };
        items.add(item);return item;
    }
}
