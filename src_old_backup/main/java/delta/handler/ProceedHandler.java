package delta.handler;

import delta.lib.javassist.ASTList;
import delta.lib.javassist.Bytecode_2;
import delta.lib.javassist.CompileError;
import delta.lib.javassist.JvstCodeGen;
import delta.lib.javassist.JvstTypeChecker;

public interface ProceedHandler {
    void a(JvstCodeGen jvstCodeGen, Bytecode_2 bytecode_2, ASTList aSTList) throws CompileError;

    void a(JvstTypeChecker jvstTypeChecker, ASTList aSTList) throws CompileError;
}
