package android.view;
public interface Menu { MenuItem findItem(int id); MenuItem getItem(int index); int size(); MenuItem add(int group,int id,int order,CharSequence label); }
