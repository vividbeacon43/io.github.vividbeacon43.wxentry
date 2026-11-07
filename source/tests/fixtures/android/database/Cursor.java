package android.database;
public interface Cursor { boolean moveToFirst(); int getInt(int column); long getLong(int column); void close();
 default boolean moveToNext(){return false;} default String getString(int column){return null;}
}
