package command;

import protocol.response.Response;
import storage.KeyValueStore;

import java.util.List;
import java.util.Locale;

public class CommnadHandler {
    private final KeyValueStore kvs ;

    public CommnadHandler(KeyValueStore kvs ) {
        this.kvs = kvs;
    }

    public Response handle(List<String> Pieces)  {
        if (Pieces == null || Pieces.isEmpty()) {
            return new Response.Error("ERR empty command");
        }
        return switch (Pieces.getFirst().toUpperCase(Locale.ROOT)){
            case "SET"  -> handleSet(Pieces);
            case "GET"  -> handleGet(Pieces);
            case "DEL"  -> handleDelete(Pieces);
            case "INCR" -> handleIncr(Pieces);
            case "DECR" -> handleDecr(Pieces);
            case "SETNX"-> handleSetnx(Pieces);
            default -> new Response.Error("ERR unknown command") ;
        };
    }

    private Response handleSet(List<String> Pieces)  {
            if(Pieces.size()==3){
                kvs.set(Pieces.get(1), Pieces.get(2));
                return new Response.SimpleString("OK");
            }
            return new Response.Error("ERR wrong number of arguments");
    }

    private Response handleGet(List<String> Pieces){
            if(Pieces.size()==2){
                String result =  kvs.get(Pieces.get(1));
                return result==null?new Response.NullValue(): new Response.BulkString(result);
            }
            return new Response.Error("ERR wrong number of arguments");
    }

    private Response handleDelete(List<String> Pieces){
            if(Pieces.size()==2){
                return new Response.LongValue(kvs.del(Pieces.get(1))?1:0);
            }
        return new Response.Error("ERR wrong number of arguments");

    }

    private Response handleIncr(List<String> Pieces){
        if(Pieces.size()==2){
            long value;
            try{
                value = kvs.incr(Pieces.get(1));
            }catch(NumberFormatException e){
                return new Response.Error("ERR value is not an integer");
            }
               return new Response.LongValue(value);
        }
        return new Response.Error("ERR wrong number of arguments");
    }

    private Response handleDecr(List<String> Pieces){
        if(Pieces.size()==2){
            long value;
            try{
                value = kvs.decr(Pieces.get(1));
            }catch(NumberFormatException e){
                return new Response.Error("ERR value is not an integer");
            }
            return new Response.LongValue(value);
        }
        return new Response.Error("ERR wrong number of arguments");
    }

    private Response handleSetnx(List<String> Pieces){
        if(Pieces.size()==3){
            return new Response.LongValue( kvs.setnx(Pieces.get(1), Pieces.get(2))?1:0);
        }
        return new Response.Error("ERR wrong number of arguments");

    }
}
