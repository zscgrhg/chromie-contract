package com.zte.crm.chromie.contract.proc;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.zte.crm.chromie.contract.ProcessorSupport;
import com.zte.crm.chromie.contract.anno.Contract;
import com.zte.crm.chromie.contract.anno.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@SupportedAnnotationTypes("com.zte.crm.chromie.contract.anno.Producer")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class DelegateCodeGen extends ProcessorSupport {

    public static final String CLASS_AUTOWIRED = Autowired.class.getCanonicalName();
    public static final String CLASS_RC = RestController.class.getCanonicalName();
    public static final String CLASS_CONTRACT = Contract.class.getCanonicalName();
    public static final ConcurrentHashMap<String, Boolean> HISTORY = new ConcurrentHashMap<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> annotated = roundEnv.getElementsAnnotatedWith(Producer.class);
        annotated.forEach(element -> {
            assert element instanceof Symbol.ClassSymbol;
            Symbol.ClassSymbol producerClazz = (Symbol.ClassSymbol) element;
            assert producerClazz.type instanceof Type.ClassType;
            Type.ClassType ctype = (Type.ClassType) producerClazz.type;
            process(producerClazz, ctype, true);
        });
        return true;
    }

    private void process(Symbol.ClassSymbol root, Type.ClassType start, boolean test) {
        if (start.isInterface() && isContract(start)) {
            genDelegate(root, start);
        }
        Stream.of(start.all_interfaces_field, start.interfaces_field)
                .filter(Objects::nonNull)
                .flatMap(t -> t.stream())
                .filter(t -> t instanceof Type.ClassType)
                .map(t -> (Type.ClassType) t)
                .forEach(t -> process(root, t, true));

        if (start.supertype_field != null && start.supertype_field instanceof Type.ClassType) {
            process(root, (Type.ClassType) start.supertype_field, true);
        } else if (test && start.tsym != null
                && start.tsym.type != null
                && start.tsym.type instanceof Type.ClassType) {
            process(root, (Type.ClassType) start.tsym.type, false);
        }

    }

    private boolean isContract(Type.ClassType classType) {
        return (classType.asElement()
                .getDeclarationAttributes()
                .stream()
                .filter(t -> Contract.class.getCanonicalName()
                        .equals(t.type.tsym.toString()))
                .findAny()
                .isPresent()

                || classType.getAnnotation(Contract.class) != null);
    }

    private void genDelegate(Symbol.ClassSymbol jcClassDecl, Type.ClassType contract) {

        Boolean exist = HISTORY.putIfAbsent(contract.tsym.toString(), Boolean.TRUE);
        if (exist != null && exist) {
            return;
        }
        final String simpleName = "DelegateOf" + contract.tsym.name.toString();
        final String genPkgName = contract.tsym.owner.toString() + ".codegen";
        final String genClassName = genPkgName + "." + simpleName;
        final long GEN_CLASS_FLAG = Flags.PUBLIC;
        JCTree.JCAnnotation annotation =
                make.Annotation(getJavaType(CLASS_RC),
                        List.nil());
        ListBuffer<JCTree.JCAnnotation> annos = new ListBuffer<>();
        annos.append(annotation);
        contract.asElement()
                .getDeclarationAttributes()
                .forEach(da -> {
                    if (!CLASS_RC.equals(da.type.toString())
                            && !CLASS_CONTRACT.equals(da.type.toString())) {
                        annos.append(make.Annotation(da));
                    }

                });

        JCTree.JCClassDecl generatedClass = make
                .ClassDef(make.Modifiers(GEN_CLASS_FLAG, annos.toList()),
                        javacNames.fromString(simpleName),
                        List.nil(),
                        null,
                        List.nil(),
                        List.nil());


        JCTree.JCAnnotation autowired = make.Annotation(
                getJavaType(CLASS_AUTOWIRED),
                List.nil());

        JCTree.JCVariableDecl producerVar = fieldDef(make.Modifiers(0L, List.of(autowired)),
                "producer", make.Type(contract), null);


        generatedClass.defs = generatedClass.defs.prepend(producerVar);


        java.util.List<Symbol> enclosedElements = contract.tsym.getEnclosedElements();
        enclosedElements.stream()
                .filter(it -> it instanceof Symbol.MethodSymbol)
                .map(it -> (Symbol.MethodSymbol) it)
                .forEach(symbol -> {

                    Symbol.ClassSymbol classSymbol = new Symbol.ClassSymbol(GEN_CLASS_FLAG,
                            generatedClass.name, jcClassDecl);
                    Symbol.MethodSymbol stub = new Symbol.MethodSymbol(
                            Flags.PUBLIC,
                            symbol.name,
                            symbol.type,
                            classSymbol
                    );

                    stub.params = symbol.params();
                    stub.savedParameterNames = symbol.savedParameterNames;
                    stub.appendAttributes(symbol.getDeclarationAttributes());
                    ListBuffer<JCTree.JCExpression> argExpr = new ListBuffer<>();
                    stub.params.forEach(arg -> argExpr.append(make.Ident(arg.name)));
                    JCTree.JCMethodInvocation invoke = invoke("this.producer." + stub.name, argExpr);
                    JCTree.JCMethodDecl copyed =
                            make.MethodDef(stub, block(make.Return(invoke)));


                    generatedClass.defs = generatedClass.defs.prepend(copyed);
                });


        try (Writer writer = processingEnv.getFiler()
                .createSourceFile(genClassName)
                .openWriter();
             PrintWriter printWriter = new PrintWriter(writer)) {
            printWriter.print("package " + genPkgName + ";\n");
            printWriter.print(generatedClass);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
