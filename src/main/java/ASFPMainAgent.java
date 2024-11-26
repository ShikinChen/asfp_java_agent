

import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.security.ProtectionDomain;
import java.util.Set;


public class ASFPMainAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        String projectPath = System.getenv("PROJECT_PATH");
        System.out.println("Current Project Path: " + projectPath);
        inst.addTransformer(new InstanceTrackerTransformer());
    }

    private static final String CLS_NAME_JDEPS_KT = "com/android/tools/asfp/soong/JdepsKt";

    private static class InstanceTrackerTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(ClassLoader classLoader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
            if (className != null) {
                if ((className.contains("AppUIUtil"))) {// 监控的包
                    ClassReader classReader = new ClassReader(classfileBuffer);
                    ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES);
                    ClassVisitor classVisitor = new AppUIUtilClassVisitor(classWriter);
                    classReader.accept(classVisitor, 0);
                    return classWriter.toByteArray();
                } else if (className.equals(CLS_NAME_JDEPS_KT)) {
                    ClassReader classReader = new ClassReader(classfileBuffer);
                    ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES);
                    ClassVisitor classVisitor = new JdepsKtClassVisitor(classWriter);
                    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
                    return classWriter.toByteArray();
                }
            }
            return classfileBuffer;
        }
    }

    private static class AppUIUtilClassVisitor extends ClassVisitor {
        public AppUIUtilClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            //getFrameClass是对应Ubuntu的WMClasss所以要进行修改
            if ("getFrameClass".equals(name)) {
                return new AppUIUtilMethodVisitor(mv);
            }
            return mv;
        }
    }

    static class AppUIUtilMethodVisitor extends MethodVisitor {
        public AppUIUtilMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitInsn(int opcode) {
            //修改成对应程序图标的StartupWMClass值
            mv.visitLdcInsn("studio-asfp");
            mv.visitInsn(Opcodes.ARETURN);
        }
    }


    private static class JdepsKtClassVisitor extends ClassVisitor {
        public JdepsKtClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if ("parseJdeps".equals(name)) {
                return new JdepsKtMethodVisitor(mv);
            }
            return mv;
        }
    }

    static class JdepsKtMethodVisitor extends MethodVisitor {
        public JdepsKtMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.ARETURN) {
                // 先获取栈中的原 Map 对象
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn("platformprotoslite");
                mv.visitLdcInsn("platformprotosnano");
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "remove", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
                // 弹出 remove 返回值（被移除的对象值）
                mv.visitInsn(Opcodes.POP);
                mv.visitInsn(Opcodes.ARETURN);
            } else {
                super.visitInsn(opcode);
            }
        }
    }
}
