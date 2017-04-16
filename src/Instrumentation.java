import BIT.highBIT.*;
import java.io.File;
import java.util.Enumeration;
import java.util.Vector;
import java.util.Arrays;
import java.lang.Thread;


public class Instrumentation {

  private static int dynMethodCount = 0;
  private static int dynBbCount = 0;
  private static int dynInstrCount = 0;

  private static int newcount = 0;
  private static int newarraycount = 0;
  private static int anewarraycount = 0;
  private static int multianewarraycount = 0;

  public static synchronized void printDynamic(String _) {
    System.out.println("Dynamic information summary:");
    System.out.println("Number of methods:      " + dynMethodCount);
    System.out.println("Number of basic blocks: " + dynBbCount);
    System.out.println("Number of instructions: " + dynInstrCount);

    if (dynMethodCount == 0) {
      return;
    }

    float instrPerBb = (float) dynInstrCount / (float) dynBbCount;
    float instrPerMethod = (float) dynInstrCount / (float) dynMethodCount;
    float bbPerMethod = (float) dynBbCount / (float) dynMethodCount;

    System.out.println("Average number of instructions per basic block: " + instrPerBb);
    System.out.println("Average number of instructions per method:      " + instrPerMethod);
    System.out.println("Average number of basic blocks per method:      " + bbPerMethod);
  }

  public static synchronized void printMemory(String _) {
    System.out.println("Allocations summary:");
    System.out.println("new:            " + newcount);
    System.out.println("newarray:       " + newarraycount);
    System.out.println("anewarray:      " + anewarraycount);
    System.out.println("multianewarray: " + multianewarraycount);
  }

  public static synchronized void init(String _) {
    System.out.println("------------- New Metrics Block -------------");
    System.out.println("Thread id: " + Thread.currentThread().getId());
    dynMethodCount = 0;
    dynBbCount = 0;
    dynInstrCount = 0;
    newcount = 0;
    newarraycount = 0;
    anewarraycount = 0;
    multianewarraycount = 0;
  }

  public static synchronized void dynInstrCount(int incr) {
    dynInstrCount += incr;
    dynBbCount++;
  }

  public static synchronized void dynMethodCount(int incr) {
    dynMethodCount++;
  }

  public static synchronized void allocCount(int type) {
    switch(type) {
    case InstructionTable.NEW:
      newcount++;
      break;
    case InstructionTable.newarray:
      newarraycount++;
      break;
    case InstructionTable.anewarray:
      anewarraycount++;
      break;
    case InstructionTable.multianewarray:
      multianewarraycount++;
      break;
    }
  }


  public static void doAnalysis(File inClass, File outDir, String hookMethod) throws Exception {

    String filename = inClass.getName();
    if (!filename.endsWith(".class")) throw new Exception();
    String inFilename = inClass.getAbsolutePath();
    String outFilename = outDir.getAbsolutePath() + System.getProperty("file.separator") + filename;
    ClassInfo ci = new ClassInfo(inFilename);

    //--- Analysis methods
    ci.addBefore("Instrumentation", "init", "null");
    Instrumentation.dynamicAnalysis(ci, hookMethod);
    Instrumentation.memoryAnalysis(ci);
    //---

    ci.write(outFilename);
  }

  public static void dynamicAnalysis(ClassInfo ci, String hookMethod) {
    for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
      Routine routine = (Routine) e.nextElement();
      routine.addBefore("Instrumentation", "dynMethodCount", new Integer(1));
      if(routine.getMethodName().equals(hookMethod)) {
        routine.addAfter("Instrumentation", "printDynamic", "null");
        routine.addAfter("Instrumentation", "printMemory", "null");
      }

      for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
        BasicBlock bb = (BasicBlock) b.nextElement();
        bb.addBefore("Instrumentation", "dynInstrCount", new Integer(bb.size()));
      }
    }
  }

  public static void memoryAnalysis(ClassInfo ci) {
    for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
      Routine routine = (Routine) e.nextElement();
      InstructionArray instructions = routine.getInstructionArray();

      for (Enumeration instrs = instructions.elements(); instrs.hasMoreElements(); ) {
        Instruction instr = (Instruction) instrs.nextElement();
        int opcode=instr.getOpcode();
        if ((opcode==InstructionTable.NEW) ||
        (opcode==InstructionTable.newarray) ||
        (opcode==InstructionTable.anewarray) ||
        (opcode==InstructionTable.multianewarray)) {
          instr.addBefore("Instrumentation", "allocCount", new Integer(opcode));
        }
      }
    }
  }

  public static void printUsage() {
    System.out.println("Run `java <Instrumented class> <input-path-for-class-to-be-instrumented> <output-path-for-instrumented-class>`");
  }

  public static void main(String argv[]) {
    try {
      File inClass = new File(argv[0]);
      File outDir = new File(argv[1]);
      String hookMethod = argv[2];

      if (inClass.exists() && outDir.isDirectory()) {
        doAnalysis(inClass, outDir, hookMethod);
      }
      else {
        printUsage();
      }
    }
    catch (Exception e) {
      printUsage();
    }
  }
}
