package com.datashield.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;
import java.sql.Connection;
import java.sql.Statement;

public class DataShieldAgent {

        public static void premain(String agentArgs, Instrumentation inst) {
                System.out.println("🛡️ DataShield Agent Loaded (v2 - with PreparedStatement support)");

                new AgentBuilder.Default()
                                .type(ElementMatchers.isSubTypeOf(Statement.class))
                                .transform((builder, type, classLoader, module,
                                                protectionDomain) -> builder.visit(Advice.to(SqlInterceptorAdvice.class)
                                                                .on(ElementMatchers.named("executeQuery")
                                                                                .or(ElementMatchers
                                                                                                .named("executeUpdate"))
                                                                                .or(ElementMatchers.named("execute")))))
                                .installOn(inst);

                // NEW: Intercept Connection.prepareStatement
                new AgentBuilder.Default()
                                .type(ElementMatchers.isSubTypeOf(Connection.class))
                                .transform((builder, type, classLoader, module,
                                                protectionDomain) -> builder.visit(Advice
                                                                .to(PrepareStatementAdvice.class)
                                                                .on(ElementMatchers.named("prepareStatement")
                                                                                .and(ElementMatchers.takesArgument(0,
                                                                                                String.class)) // التأكد
                                                                                                               // إن أول
                                                                                                               // بارامتر
                                                                                                               // String
                                                                )))
                                .installOn(inst);
        }
}