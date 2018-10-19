package com.zte.crm.chromie.contract;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;

public abstract class ProcessorSupport extends AbstractProcessor {
    protected Messager javacMessager;
    protected JavacTrees javacTrees;
    protected TreeMaker make;
    protected Names javacNames;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.javacMessager = processingEnv.getMessager();
        this.javacTrees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.make = TreeMaker.instance(context);
        this.javacNames = Names.instance(context);
    }

    protected JCTree.JCExpression getJavaType(String javaTypeName) {
        return dottedIdent(javaTypeName, 0);
    }

    protected JCTree.JCExpression dottedIdent(String dotted, int pos) {
        if (pos >= 0) {
            make.at(pos);
        }
        String[] idents = dotted.split("\\.");
        JCTree.JCExpression ret = make.Ident(javacNames.fromString(idents[0]));

        for (int i = 1; i < idents.length; i++) {
            ret = make.Select(ret, javacNames.fromString(idents[i]));
        }
        return ret;
    }

    protected JCTree.JCTypeCast castIdent(String id, String javaType) {
        return make
                .TypeCast(getJavaType(javaType),
                        make.Ident(javacNames.fromString(id)));
    }

    protected JCTree.JCTypeCast castIdent(String id, JCTree.JCExpression javaType) {
        return make
                .TypeCast(javaType,
                        make.Ident(javacNames.fromString(id)));
    }

    protected JCTree.JCVariableDecl fieldDef(JCTree.JCModifiers mods, String name, JCTree.JCExpression javaType, JCTree.JCExpression init) {
        return make.VarDef(mods,
                javacNames.fromString(name),
                javaType,
                init);
    }

    protected JCTree.JCVariableDecl varDef(long modifires, String name, JCTree.JCExpression javaType, JCTree.JCExpression init) {
        return make.VarDef(make.Modifiers(modifires),
                javacNames.fromString(name),
                javaType,
                init);
    }

    protected JCTree.JCVariableDecl varDef(long modifires, String name, String javaType, JCTree.JCExpression init) {
        return varDef(modifires, name, getJavaType(javaType), init);
    }

    protected JCTree.JCVariableDecl varDef(String name, JCTree.JCExpression javaType, JCTree.JCExpression init) {
        return varDef(0L, name, javaType, init);
    }

    protected JCTree.JCVariableDecl varDef(String name, String javaType, JCTree.JCExpression init) {
        return varDef(name, getJavaType(javaType), init);
    }

    protected JCTree.JCThrow throwByName(String name, String msg) {
        JCTree.JCExpression exception = make.NewClass(null, List.<JCTree.JCExpression>nil(),
                getJavaType(name),
                List.<JCTree.JCExpression>of(make.Literal(TypeTag.CLASS, msg)), null);
        return make.Throw(exception);
    }

    protected JCTree.JCThrow throwByName(String name) {
        JCTree.JCExpression exception = make.NewClass(null, List.<JCTree.JCExpression>nil(),
                getJavaType(name),
                List.<JCTree.JCExpression>nil(), null);
        return make.Throw(exception);
    }

    protected JCTree.JCReturn returnRef(String name) {
        return make.Return(make.Ident(javacNames.fromString(name)));
    }

    protected JCTree.JCReturn returnField(String name) {
        return make.Return(dottedIdent(name, -1));
    }

    protected JCTree.JCReturn returnInvoke(JCTree.JCMethodInvocation invocation) {
        //make.Return(make.Literal(TypeTag.VOID));
        return make.Return(invocation);
    }

    protected JCTree.JCReturn returnInvoke(String methodRef, ListBuffer<JCTree.JCExpression> args) {
        return returnInvoke(make.Apply(List.<JCTree.JCExpression>nil(), dottedIdent(methodRef, -1), args.toList()));
    }

    protected JCTree.JCReturn returnInvoke(String varRef, Name name, ListBuffer<JCTree.JCExpression> args) {
        JCTree.JCFieldAccess methodRef = make.Select(make.Ident(javacNames.fromString(varRef)),
                name);
        return returnInvoke(make.Apply(List.<JCTree.JCExpression>nil(), methodRef, args.toList()));
    }

    protected JCTree.JCMethodInvocation invoke(JCTree.JCExpression methodRef, List<JCTree.JCExpression> args) {
        return make.Apply(List.<JCTree.JCExpression>nil(), methodRef, args);
    }


    protected JCTree.JCMethodInvocation invoke(String methodRef, List<JCTree.JCExpression> args) {
        return invoke(dottedIdent(methodRef, -1), args);
    }

    protected JCTree.JCMethodInvocation invoke(String methodRef, ListBuffer<JCTree.JCExpression> args) {
        return invoke(methodRef, args.toList());
    }


    protected JCTree.JCMethodInvocation invoke(String methodRef) {
        return invoke(methodRef, List.<JCTree.JCExpression>nil());
    }

    protected JCTree.JCMethodInvocation invokeWithVarDecl(String methodRef, List<JCTree.JCVariableDecl> params) {
        ListBuffer<JCTree.JCExpression> buffer = new ListBuffer<>();
        for (JCTree.JCVariableDecl param : params) {
            buffer.append(make.Ident(param.name));
        }
        return invoke(methodRef, buffer.toList());
    }

    protected JCTree.JCMethodInvocation invokeWithVarDecl(JCTree.JCExpression methodRef, List<JCTree.JCVariableDecl> params) {
        ListBuffer<JCTree.JCExpression> buffer = new ListBuffer<>();
        for (JCTree.JCVariableDecl param : params) {
            buffer.append(make.Ident(param.name));
        }
        return invoke(methodRef, buffer.toList());
    }

    protected JCTree.JCMethodInvocation invoke(String varRef, Name name, List<JCTree.JCExpression> args) {

        return invoke(make.Select(dottedIdent(varRef, -1),
                name), args);
    }

    protected JCTree.JCMethodInvocation invoke(String varRef, Name name, ListBuffer<JCTree.JCExpression> buffer) {

        return invoke(varRef, name, buffer.toList());
    }

    protected JCTree.JCMethodInvocation invokeWithVarDecl(String varRef, Name name, List<JCTree.JCVariableDecl> params) {
        return invokeWithVarDecl(make.Select(make.Ident(javacNames.fromString(varRef)),
                name), params);
    }

    protected JCTree.JCBlock block(JCTree.JCStatement... statements) {
        ListBuffer<JCTree.JCStatement> buffer = new ListBuffer<>();
        buffer.appendArray(statements);
        return make.Block(0, buffer.toList());
    }
}
