package edu.baylor;

import javassist.*;
import javassist.bytecode.*;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;

import java.io.File;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class StaticAnalyzerApplication implements CommandLineRunner {
    private static final String jarPath = "/Users/diptads/Downloads/himel-java/java-execution-paths/sample/target/sample-0.0.1.jar";
    private static final String prefix = "com.example";
    private static final Graph<String, DefaultEdge> directedGraph = new DefaultDirectedGraph<>(DefaultEdge.class);

    public static void main(String[] args) {
        SpringApplication.run(StaticAnalyzerApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        analyze();
    }

    public static void analyze() throws Exception {
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jarPath);

        Set<String> classNames = getClassNamesFromJarFile(new File(jarPath), prefix);

        for (String cn : classNames) {
            try {
                CtClass cc = pool.get(cn);
                CtMethod[] methods = cc.getDeclaredMethods();
                for (CtMethod method : methods) {
                    analyzeControlFlowPaths(method);
                }
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }

        Set<String> sources = new HashSet<>();
        Set<String> sinks = new HashSet<>();

        for (String node : directedGraph.vertexSet()) {
            if (directedGraph.inDegreeOf(node) == 0) {
                sources.add(node);
            }
            if (directedGraph.outDegreeOf(node) == 0) {
                sinks.add(node);
            }
        }

        List<GraphPath<String, DefaultEdge>> paths = new AllDirectedPaths<>(directedGraph)
                .getAllPaths(sources, sinks, true, 100);

        System.out.println("Execution paths:");
        for (GraphPath<String, DefaultEdge> path : paths) {
            if (path.getLength() > 0) {
                System.out.println(path);
            }
        }

    }

    public static Set<String> getClassNamesFromJarFile(File targetJarFile, String prefix) throws Exception {
        Set<String> classNames = new HashSet<>();
        try (JarFile jarFile = new JarFile(targetJarFile)) {
            Enumeration<JarEntry> e = jarFile.entries();
            while (e.hasMoreElements()) {
                JarEntry jarEntry = e.nextElement();
                if (jarEntry.getName().endsWith(".class")) {
                    String className = jarEntry.getName().replace("/", ".").replace(".class", "");
                    if (className.startsWith(prefix)) {
                        classNames.add(className);
                    }
                }
            }
            return classNames;
        }
    }

    private static void analyzeControlFlowPaths(CtMethod method) {
        String currMethodName = method.getLongName().split("\\(")[0];
        directedGraph.addVertex(currMethodName);

        try {
            MethodInfo methodInfo = method.getMethodInfo();
            CodeAttribute codeAttribute = methodInfo.getCodeAttribute();

            if (codeAttribute == null) {
                return; // skip methods without code (e.g. abstract, native)
            }

            CodeIterator codeIterator = codeAttribute.iterator();

            while (codeIterator.hasNext()) {
                int position = codeIterator.next();
                int opcode = codeIterator.byteAt(position) & 0xFF;

                if (Mnemonic.OPCODE[opcode].equals("invokestatic") ||
                        Mnemonic.OPCODE[opcode].equals("invokevirtual") ||
                        Mnemonic.OPCODE[opcode].equals("invokeinterface")) {

                    int index = codeIterator.u16bitAt(position + 1);
                    ConstPool constPool = codeAttribute.getConstPool();
                    String className = constPool.getMethodrefClassName(index);
                    String methodName = constPool.getMethodrefName(index);
                    String executingMethod = className + "." + methodName;

                    if (executingMethod.startsWith(prefix)) {
                        directedGraph.addVertex(executingMethod);
                        directedGraph.addEdge(currMethodName, executingMethod);
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}
