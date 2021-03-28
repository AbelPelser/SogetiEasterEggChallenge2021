package shared;

import java.util.Arrays;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/*
 * This class contains configuration data shared by both the clients and the chaincode
 */
public class GlobalConfig {

    public static final String COMPOSITE_EVENT = "compositeEvent";

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public enum CcFunction {
        QUERY_HISTORY("queryHistory", 1),
        QUERY_TEST_VAR("queryTestVar", 1),
        SET_TEST_VAR("setTestVar", 2),
        GET_EGG("getEgg", 0);

        private final String name;
        private final int nArgs;

        public static CcFunction fromString(String name) {
            return Arrays.stream(CcFunction.values())
                .filter(f -> StringUtils.equals(f.getName(), name))
                .findFirst()
                .orElse(null);
        }
    }
}
