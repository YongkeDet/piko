import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.reandroid.apk.ApkModule;
import com.reandroid.arsc.value.Entry;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public class GlassNavStaticReview {
  static final String BULK="Lapp/morphe/extension/newx/misc/ProfileBulkDownload;";
  static final String SESSION="Lapp/morphe/extension/newx/misc/ProfileBulkDownload$Session;";
  static final Map<String,ClassDef> classes=new HashMap<>();
  static final List<String> checks=new ArrayList<>();
  static void check(boolean ok,String detail) { if(!ok)throw new AssertionError(detail); checks.add(detail); }
  static List<Instruction> code(Method m) {
    List<Instruction> result=new ArrayList<>();
    if(m.getImplementation()!=null)m.getImplementation().getInstructions().forEach(result::add);
    return result;
  }
  static Method method(String owner,String name) {
    List<Method> found=new ArrayList<>();
    for(Method m:classes.get(owner).getMethods())if(m.getName().equals(name))found.add(m);
    if(found.size()!=1)throw new AssertionError("Expected one method "+owner+" "+name+": "+found);
    return found.get(0);
  }
  static List<MethodReference> calls(Method m) {
    List<MethodReference> refs=new ArrayList<>();
    for(Instruction i:code(m))if(i instanceof ReferenceInstruction r&&r.getReference() instanceof MethodReference f)refs.add(f);
    return refs;
  }
  static Set<String> labels(String owner) {
    Set<String> result=new HashSet<>();
    for(Instruction i:code(method(owner,"toString")))if(i instanceof ReferenceInstruction r&&r.getReference() instanceof StringReference s)result.add(s.getString());
    return result;
  }
  static boolean call(Method m,String owner,String name) {return calls(m).stream().anyMatch(c->c.getDefiningClass().equals(owner)&&c.getName().equals(name));}
  static Map<String,String> strings(Path file)throws Exception {
    Map<String,String> result=new LinkedHashMap<>();
    NodeList list=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile()).getDocumentElement().getElementsByTagName("string");
    for(int i=0;i<list.getLength();i++) { Element e=(Element)list.item(i);result.put(e.getAttribute("name"),e.getTextContent()); }
    return result;
  }
  static String json(Object x) {
    if(x==null)return "null";
    if(x instanceof Number||x instanceof Boolean)return x.toString();
    if(x instanceof Map<?,?> map) {List<String> pairs=new ArrayList<>();map.forEach((k,v)->pairs.add(json(k.toString())+":"+json(v)));return "{"+String.join(",",pairs)+"}";}
    if(x instanceof Collection<?> list)return "["+String.join(",",list.stream().map(GlassNavStaticReview::json).toList())+"]";
    return "\""+x.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";
  }
 static final String G="Lapp/morphe/extension/newx/misc/FloatingGlassNavBar;", M="Landroidx/compose/ui/Modifier;";
 static int[] regs(Instruction i) {
  if(i instanceof FiveRegisterInstruction x)return Arrays.copyOf(new int[]{x.getRegisterC(),x.getRegisterD(),x.getRegisterE(),x.getRegisterF(),x.getRegisterG()},x.getRegisterCount());
  if(i instanceof RegisterRangeInstruction x){int[] a=new int[x.getRegisterCount()];for(int n=0;n<a.length;n++)a[n]=x.getStartRegister()+n;return a;}
  throw new AssertionError("Not invoke: "+i);
 }
 static Method resolve(MethodReference r){for(Method m:classes.get(r.getDefiningClass()).getMethods())if(m.toString().equals(r.toString()))return m;throw new AssertionError(r);}
 static MethodReference ref(Instruction i){return (MethodReference)((ReferenceInstruction)i).getReference();}
 static int index(List<Instruction> c,String owner,String name){List<Integer>a=new ArrayList<>();for(int n=0;n<c.size();n++)if(c.get(n) instanceof ReferenceInstruction r&&r.getReference() instanceof MethodReference m&&m.getDefiningClass().equals(owner)&&m.getName().equals(name))a.add(n);if(a.size()!=1)throw new AssertionError("Call cardinality "+a);return a.get(0);}
 static List<String> signature(Method m){List<String>a=new ArrayList<>();for(Instruction i:code(m)){String s=i.getOpcode().toString();if(i instanceof ReferenceInstruction r)s+=" "+r.getReference();if(i instanceof WideLiteralInstruction l)s+=" "+l.getWideLiteral();a.add(s);}return a;}
 public static void main(String[] args)throws Exception {
  var dex=DexFileFactory.loadDexContainer(new File(args[0]),Opcodes.getDefault());for(String e:dex.getDexEntryNames())for(ClassDef c:dex.getEntry(e).getDexFile().getClasses())classes.put(c.getType(),c);
  Method nativeHaze=resolve(calls(method(G,"nativeHaze")).stream().filter(r->r.getReturnType().equals(M)).findFirst().orElseThrow());
  Method glassHaze=resolve(calls(method(G,"nativeGlassHaze")).stream().filter(r->r.getReturnType().equals(M)).findFirst().orElseThrow());
  check(!nativeHaze.toString().equals(glassHaze.toString()),"Separate original and private glass Haze targets");
  check(nativeHaze.getDefiningClass().equals(glassHaze.getDefiningClass()),"Glass helper stays in the native Haze owner");
  var original=DexFileFactory.loadDexContainer(new File(args[1]),Opcodes.getDefault());Method before=null;
  for(String e:original.getDexEntryNames())for(ClassDef c:original.getEntry(e).getDexFile().getClasses())if(c.getType().equals(nativeHaze.getDefiningClass()))for(Method m:c.getMethods())if(m.toString().equals(nativeHaze.toString()))before=m;
  check(before!=null&&signature(before).equals(signature(nativeHaze)),"Shared native Haze wrapper preserves original opcodes, references and literals");
  var cc=code(glassHaze);int alpha=index(cc,G,"tintAlpha");
  check(cc.get(alpha+1).getOpcode()==Opcode.MOVE_RESULT,"Glass alpha result uses a scalar register");
  var ca=ref(cc.get(alpha+2));check(ca.getParameterTypes().equals(List.of("J","F"))&&ca.getReturnType().equals("J"),"Only native color alpha is overridden");
  check(regs(cc.get(alpha+2))[2]==((OneRegisterInstruction)cc.get(alpha+1)).getRegisterA(),"Alpha writes the native float input, preserving wide color");
  var cs=signature(glassHaze);cs.remove(alpha+1);cs.remove(alpha);check(cs.equals(signature(nativeHaze)),"Glass clone otherwise preserves blur, fallback, state and Composer flags");
  for(String n:List.of("nativeHaze","nativeGlassHaze")){var c=code(method(G,n));check(c.stream().filter(i->i.getOpcode()==Opcode.CHECK_CAST).count()==3,"All reference arguments cast in "+n);check(c.get(c.size()-1).getOpcode()==Opcode.RETURN_OBJECT,"Modifier returned by "+n);}
  check(call(method(G,"haze"),G,"nativeHaze")&&call(method(G,"haze"),G,"nativeGlassHaze")&&call(method(G,"haze"),G,"isEnabled"),"Runtime Haze selection preserves the off path");
  check(call(method(G,"decorate"),G,"isEnabled")&&call(method(G,"decorate"),G,"nativeDecorate"),"Decoration is opt-in");
  Method d=method(G,"nativeDecorate");var dc=code(d);var dr=calls(d);
  check(dr.size()==6,"Decoration has exactly six native/color calls");
  check(dr.get(0).getDefiningClass().startsWith("Landroidx/compose/foundation/layout/")&&dr.get(0).getParameterTypes().equals(List.of(M,"F","F","F","F")),"Four-sided padding precedes visual effects");
  check(dr.get(1).getParameterTypes().size()==6&&dr.get(1).getParameterTypes().get(1).equals("F")&&dr.get(1).getParameterTypes().get(3).equals("J"),"Shadow retains native wide colors and mask");
  check(dr.get(2).getDefiningClass().startsWith("Landroidx/compose/ui/draw/")&&dr.get(2).getParameterTypes().size()==2,"Capsule clipping follows outer shadow");
  check(dr.get(5).getParameterTypes().size()==4&&dr.get(5).getParameterTypes().get(2).equals("J"),"Highlight border wraps the background");
  check(dc.stream().filter(i->i instanceof WideLiteralInstruction l&&l.getWideLiteral()==Float.floatToRawIntBits(12f)).count()==2&&dc.stream().anyMatch(i->i instanceof WideLiteralInstruction l&&l.getWideLiteral()==Float.floatToRawIntBits(8f)),"12dp side margins and 8dp bottom gap emitted");
  check(dc.stream().anyMatch(i->i instanceof WideLiteralInstruction l&&l.getWideLiteral()==Float.floatToRawIntBits(.75f)),"0.75dp border emitted");
  var shape=dc.stream().filter(i->i.getOpcode()==Opcode.SGET_OBJECT).map(i->(FieldReference)((ReferenceInstruction)i).getReference()).findFirst().orElseThrow();
  check(code(method(shape.getDefiningClass(),"<clinit>")).stream().anyMatch(i->i instanceof WideLiteralInstruction l&&l.getWideLiteral()==50),"Native 50-percent rounded corners used");
  List<Method>hosts=new ArrayList<>();for(ClassDef c:classes.values())for(Method m:c.getMethods())if(call(m,G,"decorate"))hosts.add(m);
  check(hosts.size()==1,"Only the main bottom container is decorated");
  Method bar=hosts.get(0);var bc=code(bar);int decor=index(bc,G,"decorate"),haze=index(bc,G,"haze"),gate=index(bc,G,"useBlur");
  check(decor<gate&&gate<haze,"Insets decorated before both native background paths");
  check(bc.get(haze+1).getOpcode()==Opcode.MOVE_RESULT_OBJECT&&bc.get(haze+2).getOpcode()==Opcode.CHECK_CAST&&((TypeReference)((ReferenceInstruction)bc.get(haze+2)).getReference()).getType().equals(M),"Object Haze result restored to native Modifier");
  int joined=-1;for(int n=haze+3;n<bc.size();n++)if(bc.get(n).getOpcode()==Opcode.INVOKE_INTERFACE&&ref(bc.get(n)).getDefiningClass().equals(M)){if(joined!=-1)throw new AssertionError("Multiple joins");joined=n;}
  check(joined>haze,"Original background convergence retained");check(regs(bc.get(joined))[0]==regs(bc.get(decor))[0],"Decorated inset reaches join receiver");
  check(bc.subList(0,decor).stream().anyMatch(i->i instanceof ReferenceInstruction r&&r.getReference() instanceof MethodReference m&&m.getDefiningClass().startsWith("Landroidx/compose/foundation/layout/")&&m.getParameterTypes().size()==2&&m.getReturnType().equals(M)),"System navigation inset precedes glass margins");
  int en=index(bc,G,"isEnabled");check(en>decor,"Only bottom divider is gated");
  check(bc.get(en+1).getOpcode()==Opcode.MOVE_RESULT&&bc.get(en+2).getOpcode()==Opcode.IF_NEZ,"Enabled style branches over divider");
  int[]pcs=new int[bc.size()+1];for(int n=0;n<bc.size();n++)pcs[n+1]=pcs[n]+bc.get(n).getCodeUnits();
  int target=pcs[en+2]+((OffsetInstruction)bc.get(en+2)).getCodeOffset();check(target==pcs[en+4]&&ref(bc.get(en+3)).getReturnType().equals("V"),"Divider guard skips exactly one void call");
  for(Method m:List.of(bar,d,glassHaze,method(G,"nativeHaze"),method(G,"nativeGlassHaze")))for(Instruction i:code(m))if(i instanceof FiveRegisterInstruction||i instanceof RegisterRangeInstruction)for(int r:regs(i))if(r>=m.getImplementation().getRegisterCount())throw new AssertionError("Register overflow "+m);
  check(true,"All emitted invokes fit register files");
  var expected=strings(Path.of(args[3]).resolve("patches/src/main/resources/addresources/values-zh-rCN/newx/strings.xml"));
  try(ApkModule apk=ApkModule.loadApkFile(new File(args[0]))){apk.setLoadDefaultFramework(false);var table=apk.getTableBlock();for(String key:List.of("piko_newx_floating_glass_navbar_title","piko_newx_floating_glass_navbar_summary")){boolean found=false;var entries=table.getEntries(null,"string",key);while(entries.hasNext()){var e=entries.next();if("zh".equals(e.getResConfig().getLanguage())&&"CN".equals(e.getResConfig().getRegion())&&expected.get(key).equals(e.getValueAsString()))found=true;}check(found,"Compiled Chinese setting matches source: "+key);}}
  Map<String,Object>report=new LinkedHashMap<>();report.put("apk",args[0]);report.put("bar",bar.toString());report.put("original_haze",nativeHaze.toString());report.put("glass_haze",glassHaze.toString());report.put("checks",checks);report.put("passed",true);Files.writeString(Path.of(args[2]),json(report)+"\n");System.out.println("Passed "+checks.size()+" glass navigation checks");
 }
}
