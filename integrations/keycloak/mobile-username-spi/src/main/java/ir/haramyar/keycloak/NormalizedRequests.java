package ir.haramyar.keycloak;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import org.keycloak.http.HttpRequest;

/**
 * Keycloak re-reads form parameters from the HTTP request in each authenticator and form action, so rewriting a map
 * in place is not reliable. These helpers present a view of the request (and of the flow context that exposes it)
 * whose decoded form parameters carry the normalized username; every other call is delegated unchanged.
 */
final class NormalizedRequests {
  private NormalizedRequests() {}

  static MultivaluedMap<String, String> withUsername(MultivaluedMap<String, String> original, String username) {
    MultivaluedMap<String, String> copy = new MultivaluedHashMap<>();
    original.forEach((key, values) -> copy.put(key, new java.util.ArrayList<>(values)));
    copy.putSingle("username", username);
    return copy;
  }

  static HttpRequest request(HttpRequest original, MultivaluedMap<String, String> form) {
    return proxy(HttpRequest.class, original, (target, method, args) -> "getDecodedFormParameters".equals(method.getName()) ? form : invoke(method, target, args));
  }

  /** A view of a flow context ({@code FormContext}, {@code ValidationContext}, ...) returning {@code request} from getHttpRequest(). */
  static <T> T context(Class<T> type, T original, HttpRequest request) {
    return proxy(type, original, (target, method, args) -> "getHttpRequest".equals(method.getName()) ? request : invoke(method, target, args));
  }

  private interface Handler { Object handle(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable; }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, T target, Handler handler) {
    InvocationHandler invocation = (proxy, method, args) -> handler.handle(target, method, args);
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, invocation);
  }

  private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException wrapped) {
      throw wrapped.getCause();
    }
  }
}
