package com.playbit.util;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
@Component
public class Json {
    private final ObjectMapper mapper;
    public Json(ObjectMapper mapper) { this.mapper=mapper; }
    public String write(Object value) { try { return mapper.writeValueAsString(value); } catch(Exception ex) { throw new IllegalStateException(ex); } }
    public Object read(String value) { try { return mapper.readValue(value,new TypeReference<Object>(){}); } catch(Exception ex) { throw new IllegalStateException(ex); } }
}
