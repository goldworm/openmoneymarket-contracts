package finance.omm.utils.db;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import score.Address;
import score.ArrayDB;
import score.Context;
import score.DictDB;
import scorex.util.ArrayList;

public class EnumerableDictDB<K, V> {

    protected final String id;
    private final DictDB<Object, Integer> indexes;
    private final DictDB<Integer, K> keys;
    private final ArrayDB<Object> values;
    private final boolean supportedKeyType;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public EnumerableDictDB(String id, Class<K> keyClass, Class<? extends V> valueClass) {
        this.id = id;
        supportedKeyType = isSupportedKeyType(keyClass);
        // key => array index
        this.indexes = Context.newDictDB(id, Integer.class);
        // array index => key
        this.keys = Context.newDictDB(concatId("keys"), keyClass);
        // array of valueClass
        this.values = Context.newArrayDB(id, (Class<Object>) valueClass);
    }

    protected String concatId(Object id) {
        return concatId(this.id, id);
    }

    public int size() {
        return values.size();
    }

    private Object ensureKeyType(K key) {
        return supportedKeyType ? key : key.toString();
    }

    private Integer getIndex(K key) {
        return indexes.get(ensureKeyType(key));
    }

    private void setIndex(K key, Integer i) {
        indexes.set(ensureKeyType(key), i);
    }

    protected K getKey(Integer i) {
        return (i != null) ? keys.get(i) : null;
    }

    private void setKey(Integer i, K key) {
        keys.set(i, key);
    }

    @SuppressWarnings("unchecked")
    private V getValue(Integer i) {
        return (i != null) ? (V) values.get(i) : null;
    }

    private V putValue(Integer i, V value) {
        V old = getValue(i);
        if (old == null) {
            values.add(value);
        } else {
            values.set(i, value);
        }
        return old;
    }

    @SuppressWarnings("unchecked")
    private V removeValue(Integer i) {
        V old = getValue(i);
        if (old != null) {
            V last = (V) values.pop();
            if (i != values.size()) {
                values.set(i, last);
            }
        }
        return old;
    }

    public boolean containsKey(K key) {
        return getIndex(key) != null;
    }

    public boolean containsValue(V value) {
        int size = size();
        for (int i = 0; i < size; i++) {
            if (getValue(i).equals(value)) {
                return true;
            }
        }
        return false;
    }

    public V getByIndex(int i) {
        return getValue(i);
    }

    public V get(K key) {
        Integer i = getIndex(key);
        return getValue(i);
    }

    public V getOrDefault(K key, V defaultValue) {
        V value = get(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    public V put(K key, V value) {
        Integer i = getIndex(key);
        V old = putValue(i, value);
        if (old == null) {
            i = values.size() - 1;
            setIndex(key, i);
            setKey(i, key);
        }
        return old;
    }

    public V remove(K key) {
        Integer i = getIndex(key);
        V old = removeValue(i);
        if (old != null) {
            setIndex(key, null);
            Integer lastIdx = values.size();
            if (i.equals(lastIdx)) {
                //remove lastKey
                setKey(i, null);
            } else {
                //update lastKey
                K lastKey = getKey(lastIdx);
                setIndex(lastKey, i);
                setKey(i, lastKey);
            }
        }
        return old;
    }

    public void clear() {
        int size = size();
        for (int i = 0; i < size; i++) {
            K key = keys.get(i);
            keys.set(i, null);
            indexes.set(ensureKeyType(key), null);
            values.removeLast();
        }
    }

    public K[] keyArray() {
        int size = size();
        K[] keySet = (K[]) new Object[size];
        for (int i = 0; i < size; i++) {
            keySet[i] = getKey(i);
        }
        return keySet;
    }

    public List<K> keySet(List<Address> excludes) {
        List<K> keySet = new ArrayList<>();
        int size = size();
        for (int i = 0; i < size; i++) {
            K k = getKey(i);
            if (!excludes.contains(k)) {
                keySet.add(k);
            }
        }
        return keySet;
    }

    public List<K> keySet() {
        return keySet(List.of());
    }

    public List<Object> supportedKeySet() {
        List<Object> keySet = new ArrayList<>();
        int size = size();
        for (int i = 0; i < size; i++) {
            keySet.add(ensureKeyType(getKey(i)));
        }
        return keySet;
    }

    public List<V> values() {
        List<V> values = new ArrayList<>();
        int size = size();
        for (int i = 0; i < size; i++) {
            values.add(getValue(i));
        }
        return values;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<K, V> toMap() {
        int size = size();
        Map.Entry[] entries = new Map.Entry[size];
        for (int i = 0; i < size; i++) {
            entries[i] = Map.entry(getKey(i), getValue(i));
        }
        return Map.ofEntries(entries);
    }

    public Map<String, V> toMapWithKeyToString() {
        int size = size();
        Map.Entry[] entries = new Map.Entry[size];
        for (int i = 0; i < size; i++) {
            K key = getKey(i);
            if (key instanceof String) {
                entries[i] = Map.entry(key, getValue(i));
            } else {
                entries[i] = Map.entry(key.toString(), getValue(i));
            }
        }
        return Map.ofEntries(entries);
    }

    public static String concatId(String id, Object sub) {
        return id + "|" + sub.toString();
    }

    static boolean isSupportedKeyType(Class<?> clazz) {
        for (Class<?> type : supportedKeyTypes) {
            if (type.equals(clazz)) {
                return true;
            }
        }
        return false;
    }

    static Class<?>[] supportedKeyTypes = new Class<?>[]{
            String.class,
            byte[].class,
            Address.class,
            BigInteger.class,
            Byte.class,
            Short.class,
            Integer.class,
            Long.class,
            Character.class
    };
}