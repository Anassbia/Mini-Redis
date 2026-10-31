import java.util.concurrent.ConcurrentHashMap;

public class KeyValueStore {
    private final ConcurrentHashMap<String,String> map = new ConcurrentHashMap<>();

    public void set(String key,String value){
        map.put(key,value);
    }

    public String get(String key){
        return map.get(key);
    }

    public long incr (String key){

               String Updated =  map.compute(key, (k,v)->{
                   Long number ;
                   if(v==null){
                       number = 0L;
                   }
                   else{
                       number=Long.parseLong(v);
                   }
                       number++;
                       return Long.toString(number);
                });
            return Long.parseLong(Updated);
    }

    public boolean del(String key){
        return map.remove(key) != null;
    }

    public long decr(String key){
        String updated = map.compute(key,(k,v)->{
            long number = (v==null)?0L:Long.parseLong(v);
            number--;
            return Long.toString(number);
        });
        return Long.parseLong(updated);
    }

    public boolean setnx(String key,String value){
       return map.putIfAbsent(key,value) == null;
    }
}



