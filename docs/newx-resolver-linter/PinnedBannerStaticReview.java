import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import java.nio.file.*;
import java.util.*;

/** Exact final DEX proof for .10: stable Compose state, infinite duration and per-task native UI. */
public class PinnedBannerStaticReview extends UnifiedBannerStaticReview {
 static final String C="Lapp/morphe/extension/newx/misc/DownloadBannerController;", P="Lapp/morphe/extension/newx/misc/DownloadBannerPolicy;", S="Lapp/morphe/extension/newx/misc/ProfileBulkDownload$Session;";
 static void factory(int kind, boolean button) {
  Method m=method(N,"createRich");var c=code(m);Object[] r=new Object[m.getImplementation().getRegisterCount()];int p=r.length-4;
  r[p]="headline";r[p+1]="secondary";r[p+2]=kind;r[p+3]=button?"cancel":null;boolean built=false;
  for(int i=0,steps=0;i<c.size()&&steps++<200;){Instruction in=c.get(i);Opcode op=in.getOpcode();int next=i+1;
   if(op==Opcode.CONST_4||op==Opcode.CONST_16||op==Opcode.CONST)r[((OneRegisterInstruction)in).getRegisterA()]=((NarrowLiteralInstruction)in).getNarrowLiteral();
   else if(op==Opcode.SGET_OBJECT){var f=(FieldReference)((ReferenceInstruction)in).getReference();r[((OneRegisterInstruction)in).getRegisterA()]=f.getName();}
   else if(op==Opcode.NEW_INSTANCE)r[((OneRegisterInstruction)in).getRegisterA()]=new Value(((TypeReference)((ReferenceInstruction)in).getReference()).getType());
   else if(op.toString().startsWith("MOVE_OBJECT")||op==Opcode.MOVE||op==Opcode.MOVE_FROM16||op==Opcode.MOVE_16){var x=(TwoRegisterInstruction)in;r[x.getRegisterA()]=r[x.getRegisterB()];}
   else if(op==Opcode.IF_EQ){var x=(TwoRegisterInstruction)in;if(Objects.equals(r[x.getRegisterA()],r[x.getRegisterB()]))next=target(c,i);}
   else if(op.toString().startsWith("GOTO"))next=target(c,i);
   else if(op.toString().startsWith("INVOKE_")){
    var ref=ref(in);int[] a=regs(in);
    if(ref.getName().equals("<init>")&&ref.getParameterTypes().equals(List.of("Ljava/lang/String;")))((Value)r[a[0]]).text=(String)r[a[1]];
    else if(ref.getName().equals("<init>")&&ref.getDefiningClass().startsWith("Lcom/x/inappnotification/api/")){
     check(((Value)r[a[1]]).text.equals("headline")&&((Value)r[a[5]]).text.equals("secondary"),"Native headline and secondary text operands kind="+kind+" button="+button);
     check(button?Objects.equals(r[a[3]],"cancel"):zero(r[a[3]]),"Scoped primary button operand kind="+kind+" button="+button);
     check(Objects.equals(r[a[7]],kind==1?"Success":kind==2?"Error":"Default"),"Native result style kind="+kind+" button="+button);
     check(Objects.equals(r[a[8]],kind==3?"Low":"Normal"),"Progress does not block unrelated native notifications kind="+kind+" button="+button);
     int mask=((Number)r[a[a.length-1]]).intValue();
     check((mask&(4|16|128|256))==0&&(mask&8)!=0&&(mask&512)!=0&&(mask&1024)!=0,"Default mask supplies button/detail/style/priority kind="+kind+" button="+button);built=true;
    }else throw new AssertionError("Unexpected factory call "+ref);
   }else if(op==Opcode.RETURN_OBJECT){check(built&&r[((OneRegisterInstruction)in).getRegisterA()] instanceof Value,"Factory returns the single constructed model kind="+kind+" button="+button);return;}
   else if(op!=Opcode.CHECK_CAST)throw new AssertionError("Unsupported factory opcode "+op);
   i=next;
  }throw new AssertionError("Factory did not terminate");
 }
 static void sender(int kind,boolean available,Map<String,ClassDef> before) {
  Method m=method(N,"postRich");var c=code(m);Object[] r=new Object[m.getImplementation().getRegisterCount()];int p=r.length-2;r[p]="model";r[p+1]=kind;
  boolean sent=false,custom=false;
  for(int i=0,steps=0;i<c.size()&&steps++<100;){Instruction in=c.get(i);Opcode op=in.getOpcode();int next=i+1;
   if(op==Opcode.SGET_OBJECT)r[((OneRegisterInstruction)in).getRegisterA()]=available?"facade":null;
   else if(op==Opcode.SGET_WIDE){FieldReference f=(FieldReference)((ReferenceInstruction)in).getReference();r[((OneRegisterInstruction)in).getRegisterA()]=f;}
   else if(op==Opcode.NEW_INSTANCE)r[((OneRegisterInstruction)in).getRegisterA()]=new Value(((TypeReference)((ReferenceInstruction)in).getReference()).getType());
   else if(op==Opcode.CONST_4||op==Opcode.CONST_16)r[((OneRegisterInstruction)in).getRegisterA()]=((NarrowLiteralInstruction)in).getNarrowLiteral();
   else if(op.toString().startsWith("MOVE_OBJECT")||op==Opcode.MOVE||op==Opcode.MOVE_FROM16){var x=(TwoRegisterInstruction)in;r[x.getRegisterA()]=r[x.getRegisterB()];}
   else if(op==Opcode.IF_EQZ){if(zero(r[((OneRegisterInstruction)in).getRegisterA()]))next=target(c,i);}
   else if(op==Opcode.IF_NE){var x=(TwoRegisterInstruction)in;if(!Objects.equals(r[x.getRegisterA()],r[x.getRegisterB()]))next=target(c,i);}
   else if(op.toString().startsWith("GOTO"))next=target(c,i);
   else if(op.toString().startsWith("INVOKE_")){
    var ref=ref(in);int[] a=regs(in);
    if(ref.getName().equals("<init>")){
     check(kind==3&&ref.getParameterTypes().equals(List.of("J")),"Only progress constructs a custom duration");
     FieldReference infinity=(FieldReference)r[a[1]];
     check(infinity.getDefiningClass().equals("Lkotlin/time/Duration;")&&infinity.getType().equals("J"),"Infinite native Duration field uses a wide operand");
     var clinit=before.get(infinity.getDefiningClass()).getMethods();Method init=null;for(Method x:clinit)if(x.getName().equals("<clinit>"))init=x;
     check(code(init).stream().anyMatch(x->x instanceof WideLiteralInstruction l&&l.getWideLiteral()==4611686018427387903L),"Original Kotlin duration initialization contains positive infinity");custom=true;
    }else{
     check(Objects.equals(r[a[0]],"facade")&&Objects.equals(r[a[1]],"model"),"Post sends the retained model kind="+kind+" available="+available);
     check(kind==3?custom&&a.length==3&&r[a[2]] instanceof Value:!custom&&a.length==2,"Progress uses infinite duration; result uses native timeout kind="+kind);sent=true;
    }
   }else if(op==Opcode.RETURN){check(Objects.equals(r[((OneRegisterInstruction)in).getRegisterA()],available?1:0)&&sent==available,"Absent facade queues without an accidental dispatch kind="+kind+" available="+available);return;}
   else if(op!=Opcode.CHECK_CAST)throw new AssertionError("Unsupported post opcode "+op);
   i=next;
  }throw new AssertionError("Post did not terminate");
 }
 // Symbolically follow the real compiled hasUnifiedHost/unified branches; other predicates fork.
 record State(int pc,Set<Integer> flags){}
 static Set<Integer> unifiedPath(Method m){
  var c=code(m);var seen=new HashSet<State>();var out=new HashSet<Integer>();var todo=new ArrayDeque<State>();todo.add(new State(0,Set.of()));
  while(!todo.isEmpty()){
   State s=todo.remove();int n=s.pc();if(n<0||n>=c.size()||!seen.add(s))continue;out.add(n);Instruction in=c.get(n);Opcode op=in.getOpcode();Set<Integer> flags=new HashSet<>(s.flags());
   if(op==Opcode.IGET_BOOLEAN&&((ReferenceInstruction)in).getReference() instanceof FieldReference f&&f.getName().equals("unified"))flags.add(((OneRegisterInstruction)in).getRegisterA());
   else if(op==Opcode.MOVE_RESULT&&n>0&&c.get(n-1) instanceof ReferenceInstruction rr&&rr.getReference() instanceof MethodReference r&&r.getDefiningClass().equals(N)&&r.getName().equals("hasUnifiedHost"))flags.add(((OneRegisterInstruction)in).getRegisterA());
   else if(op.setsRegister()&&in instanceof OneRegisterInstruction r)flags.remove(r.getRegisterA());
   if(op.toString().startsWith("RETURN")||op==Opcode.THROW)continue;
   if(op==Opcode.IF_EQZ||op==Opcode.IF_NEZ){int reg=((OneRegisterInstruction)in).getRegisterA();if(s.flags().contains(reg)){todo.add(new State(op==Opcode.IF_NEZ?target(c,n):n+1,Set.copyOf(flags)));continue;}}
   if(op.toString().startsWith("GOTO")){todo.add(new State(target(c,n),Set.copyOf(flags)));continue;}
   if(op.toString().startsWith("IF_"))todo.add(new State(target(c,n),Set.copyOf(flags)));
   if(op==Opcode.PACKED_SWITCH||op==Opcode.SPARSE_SWITCH)throw new AssertionError("Unexpected switch in gated UI "+m);
   todo.add(new State(n+1,Set.copyOf(flags)));
  }return out;
 }
 static boolean pathCalls(Method m,Set<Integer> path,String owner,String name){var c=code(m);return path.stream().anyMatch(n->c.get(n) instanceof ReferenceInstruction rr&&rr.getReference() instanceof MethodReference r&&(owner==null||r.getDefiningClass().equals(owner))&&r.getName().equals(name));}
 public static void main(String[] a)throws Exception{
  classes.putAll(load(a[0]));var before=load(a[1]);
  var enabled=code(method(N,"hasUnifiedHost"));check(enabled.get(0) instanceof NarrowLiteralInstruction l&&l.getNarrowLiteral()==1,"Validated native adapter enabled");
  for(int k=0;k<5;k++)for(boolean button:List.of(false,true))factory(k,button);
  for(int k=0;k<5;k++)for(boolean available:List.of(false,true))sender(k,available,before);
  var actors=new ArrayList<Method>();for(ClassDef cls:classes.values())for(Method m:cls.getMethods())if(call(m,N,"interceptNative"))actors.add(m);
  check(actors.size()==1,"Exactly one actor gate");Method actor=actors.get(0);var ac=code(actor);int gate=index(ac,N,"interceptNative");
  check(ac.get(gate-1).getOpcode()==Opcode.MOVE_RESULT_OBJECT&&ac.get(gate+1).getOpcode()==Opcode.MOVE_RESULT&&ac.get(gate+2).getOpcode()==Opcode.IF_NEZ,"Actor decoded-entry invoke/result and consumed branch");
  check(normalized(ac,Set.of(gate,gate+1,gate+2)).equals(normalized(code(exact(before,actor)),Set.of())),"All original actor instructions and control flow preserved");
  check(ac.get(target(ac,gate+2)).getOpcode().toString().startsWith("GOTO"),"Consumed Show resumes the original native actor loop");
  Method intercept=method(N,"interceptNative");var nativeCalls=calls(intercept).stream().filter(r->r.getDefiningClass().startsWith("Lcom/x/inappnotification/impl/")&&r.getReturnType().equals("V")).toList();
  check(nativeCalls.size()==2&&nativeCalls.stream().noneMatch(r->r.getParameterTypes().size()==1&&r.getParameterTypes().get(0).toString().startsWith("Lcom/x/inappnotification/impl/")),"Gate never re-presents an existing progress entry");
  check(call(intercept,N,"decideNative"),"Gate delegates identity and task ownership policy");
  int lifecycle=0,textHooks=0;
  for(ClassDef cls:classes.values())for(Method m:cls.getMethods()){
   if(call(m,N,"observeNative")){
    lifecycle++;var c=code(m);int hook=index(c,N,"observeNative");check(hook>0&&ref(c.get(hook-1)).getDefiningClass().startsWith("Lkotlinx/coroutines/flow/"),"Native observation follows actual publication: "+m);
    var skip=new HashSet<Integer>();skip.add(hook);if(call(m,N,"disposingNative")){int d=index(c,N,"disposingNative");check(d==0,"Disposal captures original entry identity at method entry");skip.add(d);}
    check(normalized(c,skip).equals(normalized(code(exact(before,m)),Set.of())),"Native lifecycle original instructions preserved: "+m);
    int p=m.getImplementation().getRegisterCount()-m.getParameterTypes().size()-1;
    check(regs(c.get(hook))[0]==p,"Lifecycle callback uses original manager register: "+m);
    for(int n=0;n<hook;n++)if(c.get(n).getOpcode().setsRegister()&&c.get(n) instanceof OneRegisterInstruction r)check(r.getRegisterA()!=p,"Manager receiver live at publication index="+n+": "+m);
   }
   if(call(m,N,"progressText")){
    textHooks++;var c=code(m);int h=index(c,N,"progressText");check(h==0&&c.get(1).getOpcode()==Opcode.MOVE_RESULT_OBJECT&&c.get(2).getOpcode()==Opcode.IF_EQZ&&c.get(3).getOpcode()==Opcode.RETURN_OBJECT,"Owned literal uses reactive state; normal text retains original path");
    check(target(c,2)==4&&normalized(c,Set.of(0,1,2,3)).equals(normalized(code(exact(before,m)),Set.of())),"Original group-free localizer remains byte-identical after the conditional read");
   }
  }
  check(lifecycle==2&&textHooks==1,"Exactly present/dispose publications and one shared text localizer hooked");
  var make=calls(method(N,"makeState"));check(make.size()==1&&make.get(0).getReturnType().startsWith("Landroidx/compose/runtime/"),"Native Compose Snapshot factory");
  String state=make.get(0).getReturnType();check(classes.get(state).getSuperclass().startsWith("Landroidx/compose/runtime/snapshots/"),"Progress is Snapshot-backed");
  check(call(method(N,"readState"),state,"getValue")&&call(method(N,"writeState"),state,"setValue"),"Native reactive read/write, with no reflection or final-field mutation");
  var textUpdater=new ArrayList<Method>();for(Method m:classes.get(C).getMethods())if(call(m,N,"writeState"))textUpdater.add(m);
  check(textUpdater.size()==1,"One main-thread text-update callback");check(!call(textUpdater.get(0),N,"postRich")&&!call(textUpdater.get(0),N,"createRich"),"Progress tick updates State without resending/rebuilding the native notice");
  var postCallers=new ArrayList<Method>();for(ClassDef cls:classes.values())for(Method m:cls.getMethods())if(call(m,N,"postRich"))postCallers.add(m);
  check(postCallers.size()==1&&postCallers.get(0).getDefiningClass().equals(C)&&postCallers.get(0).getName().equals("pump"),"Only the acknowledged task queue dispatches notices");
  check(call(method(C,"pump"),P,"next")&&call(method(C,"pump"),P,"bind")&&call(method(C,"pump"),P,"bindClear"),"Queue owns task models before actor admission");
  check(call(method(P,"disposed"),"Ljava/util/ArrayDeque;","remove"),"Result FIFO advances on actual native disposal");
  Method bulkConstructor=method(S,"<init>");
  check(!pathCalls(bulkConstructor,unifiedPath(bulkConstructor),"Lapp/morphe/extension/crimera/ui/DialogView;","<init>"),"Unified bulk session never constructs the legacy automatic progress dialog");
  for(String name:List.of("begin","updateProgress","finish")){
   Method m=method(S,name);Set<Integer> path=unifiedPath(m);
   check(!pathCalls(m,path,"Lapp/morphe/extension/crimera/ui/DialogView;","show"),"Unified bulk "+name+" cannot display an automatic modal");
   check(pathCalls(m,path,N,name.equals("begin")?"startProgress":name.equals("finish")?"taskResult":"progress"),"Bulk "+name+" reaches the common native task queue");
   if(name.equals("updateProgress"))check(!pathCalls(m,path,"Landroid/widget/ProgressBar;","setProgress"),"Unified bulk progress has no competing dialog progress bar");
  }
  check(call(method(S,"cancelTransfers"),"Lapp/morphe/extension/crimera/downloader/DownloadHandle;","cancel"),"Bulk cancellation uses only this session's handles");
  check(call(method(S,"finish"),"Landroid/view/Window;","clearFlags"),"Bulk session releases its screen-awake flag");
  Method tick=method(D,"tick");var path=unifiedPath(tick);check(pathCalls(tick,path,N,"progress")&&!pathCalls(tick,path,D,"attach"),"Single-task native progress cannot create the legacy second card");
  for(String name:List.of("onCompleted","onFailed","onCancelled"))check(call(method(D,name),D,"end"),"Terminal event removes only its task progress: "+name);
  check(call(method(D,"end"),N,"finishProgress"),"Task end is keyed instead of clearing all progress");
  String status="Lapp/morphe/extension/newx/misc/NewXDownloader$StatusListener;";
  check(call(method(status,"onCompleted"),N,"taskSuccess")&&call(method(status,"onFailed"),N,"taskError")&&call(method(status,"onCancelled"),N,"taskResult"),"Engine success/error/cancel results share keyed ownership");
  for(String name:List.of("onCompleted","onFailed","onCancelled"))check(call(method(status,name),"Lapp/morphe/extension/newx/misc/ProfileBulkDownload;","isBatchRequest"),"Batch member has no individual result: "+name);
  Method merge=method("Lapp/morphe/extension/newx/misc/MediaMerger;","performMerge");check(call(merge,N,"taskSuccess")&&call(merge,N,"taskError")&&call(merge,N,"taskInfo"),"Every merge outcome uses its own local task identity");
  var report=new LinkedHashMap<String,Object>();report.put("apk",a[0]);report.put("actor",actor.toString());report.put("checks",checks);report.put("passed",true);Files.writeString(Path.of(a[2]),json(report)+"\n");System.out.println("Passed "+checks.size()+" pinned-banner checks");
 }
}
