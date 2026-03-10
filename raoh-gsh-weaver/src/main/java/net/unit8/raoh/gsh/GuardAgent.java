package net.unit8.raoh.gsh;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * Java Agent that weaves construction guards into domain classes at class-load time.
 *
 * <p>Attach this agent to the JVM with:
 * <pre>
 * -javaagent:raoh-gsh-weaver.jar=packages=com.example.domain.**
 * </pre>
 *
 * <p>The agent argument uses the same format as {@link GuardConfig#parse(String)}.
 * Multiple settings are separated by semicolons:
 * <pre>
 * -javaagent:raoh-gsh-weaver.jar=packages=com.example.domain.**;exclude=com.example.domain.internal.**
 * </pre>
 *
 * <p>For Maven Surefire, configure like:
 * <pre>{@code
 * <plugin>
 *     <artifactId>maven-surefire-plugin</artifactId>
 *     <configuration>
 *         <argLine>-javaagent:${path}/raoh-gsh-weaver.jar=packages=com.example.domain.**</argLine>
 *     </configuration>
 * </plugin>
 * }</pre>
 */
public final class GuardAgent {

    private GuardAgent() {
    }

    /**
     * Agent entry point called before {@code main} when the JVM is started with
     * {@code -javaagent:raoh-gsh-weaver.jar=...}.
     *
     * @param args the agent argument string (parsed by {@link GuardConfig#parse(String)})
     * @param inst the instrumentation instance provided by the JVM
     */
    public static void premain(String args, Instrumentation inst) {
        GuardConfig config = GuardConfig.parse(args);
        inst.addTransformer(new GuardTransformer(config));
    }

    private static final class GuardTransformer implements ClassFileTransformer {
        private final GuardConfig config;

        GuardTransformer(GuardConfig config) {
            this.config = config;
        }

        @Override
        public byte[] transform(ClassLoader loader,
                                String className,
                                Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain,
                                byte[] classfileBuffer) {
            if (className == null || !config.isTargetInternal(className)) {
                return null;
            }
            try {
                return GuardWeaver.weave(classfileBuffer);
            } catch (Exception e) {
                System.err.println("[raoh-gsh] Failed to weave " + className + ": " + e.getMessage());
                return null;
            }
        }
    }
}
