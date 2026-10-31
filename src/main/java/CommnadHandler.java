import java.util.Locale;

public class CommnadHandler {
    private final KeyValueStore kvs ;

    public CommnadHandler(KeyValueStore kvs) {
        this.kvs = kvs;
    }

    public String handle(String CommandLine){
        if(CommandLine==null ||  CommandLine.isBlank()){
            return "ERR unknown command";
        }
        CommandLine = CommandLine.trim();
        String[] Pieces = CommandLine.split("\\s+");
        return switch (Pieces[0].toUpperCase(Locale.ROOT)){
            case "SET"  -> handleSet(Pieces);
            case "GET"  -> handleGet(Pieces);
            case "DEL"  -> handleDelete(Pieces);
            case "INCR" -> handleIncr(Pieces);
            case "DECR" -> handleDecr(Pieces);
            case "SETNX"-> handleSetnx(Pieces);
            default -> "ERR unknown command" ;
        };
    }

    private String handleSet(String[] Pieces){
            if(Pieces.length==3){
                kvs.set(Pieces[1],Pieces[2]);
                return "OK";
            }
            return "ERR wrong number of arguments";
    }

    private String handleGet(String[] Pieces){
            if(Pieces.length==2){
                String result =  kvs.get(Pieces[1]);
                return result==null?"NULL":result;
            }
            return "ERR wrong number of arguments";
    }

    private String handleDelete(String[] Pieces){
            if(Pieces.length==2){
                return kvs.del(Pieces[1])?"1":"0";
            }
            return "ERR wrong number of arguments";
    }

    private String handleIncr(String[] Pieces){
        if(Pieces.length==2){
           try{
               return Long.toString(kvs.incr(Pieces[1]));
           }catch(NumberFormatException e){
               return "ERR value is not an integer";
           }
        }
        return "ERR wrong number of arguments";
    }

    private String handleDecr(String[] Pieces){
        if(Pieces.length==2){
            try{
                return Long.toString(kvs.decr(Pieces[1]));
            }catch(NumberFormatException e){
                return "ERR value is not an integer";
            }
        }
        return "ERR wrong number of arguments";
    }

    private String handleSetnx(String[] Pieces){
        if(Pieces.length==3){
            return kvs.setnx(Pieces[1],Pieces[2])?"1":"0";
        }
        return "ERR wrong number of arguments";
    }
}
