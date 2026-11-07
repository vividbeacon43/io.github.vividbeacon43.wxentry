package android.database;public interface Cursor {boolean moveToFirst();String getString(int i);long getLong(int i);int getInt(int i);void close();}
