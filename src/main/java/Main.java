import java.io.IOException;

public class Main {
    public static void main(String[] args)  {
        RedisServer redisServer = new RedisServer();
        try{
            redisServer.start();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
