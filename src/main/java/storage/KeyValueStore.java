package storage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Long.parseLong;

public class KeyValueStore {
    private final ConcurrentHashMap<String,StoredValue> map = new ConcurrentHashMap<>();

    public void set(String key,String value){
        map.put(key,StoredValue.withoutExpiration(value));
    }

    public String get(String key){
         StoredValue result = map.computeIfPresent(key, (k,v)->{
            if(v.isExpired(System.currentTimeMillis())){
                return null;
            }
            return v;
        } );
        return result == null ? null : result.value();
    }

    public long incr (String key){
        StoredValue Existing = map.compute(key,(k,v)->{
            long now = System.currentTimeMillis();
          if( v==null || v.isExpired(now)){
              return StoredValue.withoutExpiration("1");
          }
          long numericValue = parseLong(v.value());
          long updated = Math.addExact(numericValue,1);
          return new StoredValue(Long.toString(updated),v.expiresAt());
        });
        return Long.parseLong(Existing.value());
    }

    public boolean del(String key){
        AtomicBoolean deleted = new AtomicBoolean(false);
        map.computeIfPresent(key,(k,v)->{
            long currentTime = System.currentTimeMillis();
            if(!v.isExpired(currentTime)){
                deleted.set(true);
            }
            return null;
        });
        return deleted.get();
    }

    public long decr(String key){
       StoredValue Existing = map.compute(key , (k,v)->{
           long now = System.currentTimeMillis();
           if(v== null || v.isExpired(now)){
               return StoredValue.withoutExpiration("-1");
           }
           long numericValue = parseLong(v.value());
           long updated = Math.subtractExact(numericValue,1);
           return new StoredValue(Long.toString(updated),v.expiresAt());
       });
        return Long.parseLong(Existing.value());
    }

    public boolean setnx(String key,String value){
        AtomicBoolean result = new AtomicBoolean(false);
        map.compute(key,(k,v)->{
           long now = System.currentTimeMillis();
           if(v== null || v.isExpired(now)) {
               result.set(true);
               return StoredValue.withoutExpiration(value);
           }else{
               result.set(false);
               return v;
           }
       });
       return result.get();
    }

    public boolean expire(String key,long seconds){
        AtomicBoolean success = new AtomicBoolean(false);
        map.computeIfPresent(key,(k,v)->{
            long now = System.currentTimeMillis();
            if(v.isExpired(now)){
                success.set(false);
                return null;
            }
            if(seconds <= 0){
                success.set(true);
                return null;
            }
            long durationMills = Math.multiplyExact(seconds,1000);
            long deadline  = Math.addExact(now,durationMills) ;
            success.set(true);
            return new StoredValue(v.value(),deadline);
        });
        return success.get();
    }
        public long ttl(String key){
            StoredValue result = map.computeIfPresent(key,(k,v)->{
                long now = System.currentTimeMillis();
                if(v.isExpired(now)){
                    return null;
                }
                return v;
            });
            if(result == null){
                return -2;
            }
            if(result.expiresAt() == Long.MAX_VALUE){
                return -1;
            }
            long remainingtime = result.expiresAt()- System.currentTimeMillis();
            if(remainingtime <= 0){
                return -2;
            }
            return remainingtime/1000;
        }
}



