package storage;

public record StoredValue(String value,long expiresAt) {
    boolean isExpired(long now ) {return now>= expiresAt && expiresAt != Long.MAX_VALUE ;}

     static StoredValue withoutExpiration(String value ){
        return new StoredValue(value,Long.MAX_VALUE);
    }
}
