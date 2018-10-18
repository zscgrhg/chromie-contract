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
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes("com.zte.crm.chromie.contract.anno.Producer")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class DelegateCodeGen extends ProcessorSupport {

    public static final String CLASS_AUTOWIRED = Autowired.class.getCanonicalName();
    public static final String CLASS_RC = RestController.class.getCanonicalName();


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> annotated = roundEnv.getElementsAnnotatedWith(Producer.class);
        annotated.forEach(element -> {
            if (element instanceof Symbol.ClassSymbol) {
                Symbol.ClassSymbol producerClazz = (Symbol.ClassSymbol) element;
                if (producerClazz.type instanceof Type.ClassType) {
                    Type.ClassType ctype = (Type.ClassType) producerClazz.type;
                    process(producerClazz, ctype);
                }
            }
        });
        return true;
    }

    private void process(Symbol.ClassSymbol root, Type.ClassType start) {
        if (start.isInterface() && (start.asElement()
                .getDeclarationAttributes()
                .stream()
                .filter(t -> Contract.class.getCanonicalName()
                        .equals(t.type.tsym.toString()))
                .findAny()
                .isPresent()

                || start.getAnnotation(Contract.class) != null)) {
            genDelegate(root, start);
        }
        if (start.all_interfaces_field != null) {
            java.util.List<Type.ClassType> ic = start.all_interfaces_field
                    .stream()
                    .filter(t -> t instanceof Type.ClassType)
                    .map(t -> (Type.ClassType) t)
                    .collect(Collectors.toList());
            ic
                    .forEach(t -> process(root, t));
        }
        if (start.supertype_field != null && start.supertype_field instanceof Type.ClassType) {
            process(root, (Type.ClassType) start.supertype_field);
        }
    }

    private void genDelegate(Symbol.ClassSymbol jcClassDecl, Type.ClassType contract) {
        Type contractTypeSymbo = contract;
        final String simpleName = "DelegateOf" + contractTypeSymbo.tsym.name.toString();
        final String genPkgName = jcClassDecl.owner.toString();
        final String genClassName = genPkgName + "." + simpleName;
        final long GEN_CLASS_FLAG = Flags.PUBLIC;
        JCTree.JCClassDecl generatedClass = make
                .ClassDef(make.Modifiers(GEN_CLASS_FLAG),
                        javacNames.fromString(simpleName),
                        List.nil(),
                        null,
                        List.nil(),
                        List.nil());

        JCTree.JCAnnotation annotation =
                make.Annotation(getJavaType(CLASS_RC),
                        List.nil());
        if (!jcClassDecl.isInterface()) {
            generatedClass.mods.annotations = List.of(annotation);
        }

        generatedClass.implementing = List.of(make.Type(contractTypeSymbo));
        JCTree.JCAnnotation autowired = make.Annotation(
                getJavaType(CLASS_AUTOWIRED),
                List.nil());

        JCTree.JCVariableDecl producerVar = fieldDef(make.Modifiers(0L, List.of(autowired)),
                "producer", make.Type(contractTypeSymbo), null);


        generatedClass.defs = generatedClass.defs.prepend(producerVar);


        java.util.List<Symbol> enclosedElements = contractTypeSymbo.tsym.getEnclosedElements();
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
            printWriter.print("package " + genPkgName + ";");
            printWriter.print(generatedClass);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
