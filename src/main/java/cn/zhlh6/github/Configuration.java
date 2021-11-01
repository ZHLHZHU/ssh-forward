package cn.zhlh6.github;

import org.apache.commons.lang3.math.NumberUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Configuration {

    public static void load() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final Map<String, Field> fieldMap = Arrays.stream(Configuration.class.getDeclaredFields())
                .collect(Collectors.toMap(Field::getName, Function.identity()));

        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            final String key = (String) entry.getKey();
            final String value = (String) entry.getValue();
            if (!fieldMap.containsKey(key)) {
                continue;
            }
            final Field field = fieldMap.get(key);
            field.setAccessible(true);
            final Class<?> fieldType = field.getType();
            if (fieldType == Integer.class || fieldType == Long.class) {
                if (NumberUtils.isCreatable(value)) {
                    field.set(null, fieldType.getDeclaredMethod("valueOf", String.class).invoke(null, value));
                }
                continue;
            }
            field.set(null, value);
        }
    }

    private Configuration() {
    }

    /**
     * 监听地址
     */
    static String LISTEN_HOST = "0.0.0.0";
    /**
     * 监听端口
     */
    static Integer LISTEN_PORT = 22;
    /**
     * 客户端连接限速
     */
    static Long CLIENT_SPEED_LIMIT = 0L;
    /**
     * 上游地址
     */
    static String UPSTREAM_HOST = "github.com";
    /**
     * 上游端口
     */
    static Integer UPSTREAM_PORT = 22;
    /**
     * 链接上游的限速，针对每次连接，非全局限速
     */
    static Long UPSTREAM_SPEED_LIMIT = 0L;
}
