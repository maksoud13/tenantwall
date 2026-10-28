package com.tenantwall.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.sql.Connection;
import java.sql.Statement;

public class tenantwallAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("🛡️ tenantwall Agent Loaded (v4 - Hibernate + Argument)");
        TenantwallConfig.initialize(agentArgs);

        ElementMatcher<? super TypeDescription> ignore = ElementMatchers
                .nameStartsWith("net.bytebuddy.")
                .or(ElementMatchers.nameStartsWith("com.tenantwall.agent."));

        // Listener for debugging
        AgentBuilder.Listener listener = new AgentBuilder.Listener.Adapter() {
            @Override
            public void onTransformation(TypeDescription td, ClassLoader cl, JavaModule m,
                                         boolean loaded, DynamicType dt) {
                System.out.println("✅ [ByteBuddy] Transformed: " + td.getName());
            }
            @Override
            public void onDiscovery(String tn, ClassLoader cl, JavaModule m, boolean loaded) {
                if (tn.contains("StatementPreparer") || tn.contains("HikariProxyConnection")) {
                    System.out.println("🔍 [ByteBuddy] Discovered: " + tn + " loaded=" + loaded);
                }
            }
            @Override
            public void onError(String tn, ClassLoader cl, JavaModule m, boolean loaded, Throwable t) {
                if (tn.contains("StatementPreparer") || tn.contains("Connection")) {
                    System.out.println("❌ [ByteBuddy] Error: " + tn + " → " + t);
                }
            }
        };

        // 1. Hibernate — primary
        new AgentBuilder.Default()
                .ignore(ignore)
                .with(listener)
                .type(ElementMatchers
                        .named("org.hibernate.engine.jdbc.internal.StatementPreparerImpl")
                        .or(ElementMatchers.named(
                                "org.hibernate.engine.jdbc.internal.MutationStatementPreparerImpl")))
                .transform((builder, type, cl, module, pd) ->
                        builder.visit(Advice.to(HibernateStatementAdvice.class)
                                .on(ElementMatchers.named("prepareQueryStatement")
                                        .or(ElementMatchers.named("prepareStatement"))
                                        .or(ElementMatchers.named("prepareCallableStatement")))))
                .installOn(inst);

        // 2. JDBC Statement
        new AgentBuilder.Default()
                .ignore(ignore)
                .type(ElementMatchers.isSubTypeOf(Statement.class))
                .transform((builder, type, cl, module, pd) ->
                        builder.visit(Advice.to(SqlInterceptorAdvice.class)
                                .on(ElementMatchers.named("executeQuery")
                                        .or(ElementMatchers.named("executeUpdate"))
                                        .or(ElementMatchers.named("execute")))))
                .installOn(inst);

        // 3. Connection.prepareStatement — backup
        new AgentBuilder.Default()
                .ignore(ignore)
                .type(ElementMatchers.isSubTypeOf(Connection.class))
                .transform((builder, type, cl, module, pd) ->
                        builder.visit(Advice.to(PrepareStatementAdvice.class)
                                .on(ElementMatchers.named("prepareStatement"))))
                .installOn(inst);

        System.out.println("🛡️ Interceptors installed (v4)");
    }
}