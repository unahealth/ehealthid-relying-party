package com.oviva.gesundheitsid.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdDelegatingSerializer;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.gesundheitsid.util.JWKSetDeserializer.JWKDeserializer;
import java.io.IOException;

public class JsonCodec {

  private static ObjectMapper om;

  static {
    var om = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    var mod = new SimpleModule("jwks");
    mod.addDeserializer(JWK.class, new JWKDeserializer(JWK.class));
    mod.addDeserializer(JWKSet.class, new JWKSetDeserializer(JWKSet.class));
    mod.addSerializer(new StdDelegatingSerializer(JWKSet.class, new JWKSetConverter()));
    om.registerModule(mod);

    JsonCodec.om = om;
  }

  private JsonCodec() {}

  public static String writeValueAsString(Object value) {
    try {
      return om.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new SerializeException("failed to serialize value to JSON", e);
    }
  }

  public static <T> T readValue(String in, Class<T> clazz) {
    try {
      return om.readValue(in, clazz);
    } catch (IOException e) {
      throw new DeserializeException("failed to deserialize JSON", e);
    }
  }

  public static <T> T readValue(byte[] in, Class<T> clazz) {
    try {
      return om.readValue(in, clazz);
    } catch (IOException e) {
      throw new DeserializeException("failed to deserialize JSON", e);
    }
  }

  public static class JsonException extends RuntimeException {

    public JsonException(String message) {
      super(message);
    }

    public JsonException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class DeserializeException extends JsonException {

    public DeserializeException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class SerializeException extends JsonException {

    public SerializeException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
