package protocol.response;

public sealed interface Response {
    record SimpleString(String value) implements Response {}
    record BulkString(String value) implements Response {}
    record LongValue(long  value) implements Response {}
    record Error(String message ) implements Response {}
    record NullValue() implements Response {}
}
