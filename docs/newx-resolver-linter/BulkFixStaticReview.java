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

public final class BulkFixStaticReview {
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
    if(x instanceof Collection<?> list)return "["+String.join(",",list.stream().map(BulkFixStaticReview::json).toList())+"]";
    return "\""+x.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";
  }
  public static void main(String[] args)throws Exception {
    File input=new File(args[0]);Path source=Path.of(args[1]);
    var container=DexFileFactory.loadDexContainer(input,Opcodes.getDefault());
    for(String entry:container.getDexEntryNames())for(ClassDef c:container.getEntry(entry).getDexFile().getClasses())classes.put(c.getType(),c);
    for(String name:List.of("componentOf","availableTabs","selectedTab","selectTab","currentItems","pagingSnapshot","requestNext","refresh")) {
      Method m=method(BULK,name);
      check(!code(m).isEmpty()&&code(m).stream().noneMatch(i->i.getOpcode()==Opcode.THROW)&&!call(m,BULK,"unpatched"),"Direct native bridge present: "+name);
    }
    Method current=method(BULK,"currentItems");List<Instruction> c=code(current);
    String empty=null,success=null;int emptyIndex=-1;
    for(int i=0;i<c.size();i++)if(c.get(i).getOpcode()==Opcode.INSTANCE_OF&&c.get(i) instanceof ReferenceInstruction r&&r.getReference() instanceof TypeReference t) {
      Set<String> labels=labels(t.getType());
      if(labels.contains("Empty")) {empty=t.getType();emptyIndex=i;}
      if(labels.contains("Success(timelineType="))success=t.getType();
    }
    check(empty!=null&&success!=null,"Native Empty and Success states both handled");
    Set<String> common=new HashSet<>(classes.get(empty).getInterfaces());common.retainAll(classes.get(success).getInterfaces());
    check(common.size()==1,"Empty belongs to the exact Success state contract");
    int pc=0;Map<Integer,Integer> indexes=new HashMap<>();int branchPc=-1;
    for(int i=0;i<c.size();i++) {indexes.put(pc,i);if(i==emptyIndex+1)branchPc=pc;pc+=c.get(i).getCodeUnits();}
    Instruction branch=c.get(emptyIndex+1);
    check(branch.getOpcode()==Opcode.IF_EQZ,"Empty type check branches on false to Success");
    Integer target=indexes.get(branchPc+((OffsetInstruction)branch).getCodeOffset());
    check(target!=null&&c.get(target).getOpcode()==Opcode.INSTANCE_OF&&((TypeReference)((ReferenceInstruction)c.get(target)).getReference()).getType().equals(success),"Empty false branch reaches Success type check");
    boolean emptyList=false,returned=false;
    for(int i=emptyIndex+2;i<target;i++) {
      Instruction op=c.get(i);
      if(op instanceof ReferenceInstruction r&&r.getReference() instanceof MethodReference m&&m.getDefiningClass().equals("Ljava/util/Collections;")&&m.getName().equals("emptyList"))emptyList=true;
      if(op.getOpcode()==Opcode.RETURN_OBJECT)returned=true;
    }
    check(emptyList&&returned,"Native Empty returns a real empty list instead of null");
    check(calls(current).stream().anyMatch(r->r.getDefiningClass().startsWith("Lcom/x/profile/timeline/")),"Timeline polled from the selected live child");
    Method snapshot=method(BULK,"pagingSnapshot");
    check(code(snapshot).stream().anyMatch(i->i instanceof WideLiteralInstruction v&&v.getWideLiteral()==6),"Snapshot contains six values including backoff");
    check(calls(snapshot).stream().noneMatch(r->r.getName().equals("toString")),"Cursor identity does not use wrapper toString");
    List<FieldReference> fields=new ArrayList<>();
    for(Instruction i:code(snapshot))if(i instanceof ReferenceInstruction r&&r.getReference() instanceof FieldReference f)fields.add(f);
    List<FieldReference> values=fields.stream().filter(f->f.getType().equals("Ljava/lang/String;")).toList();
    check(values.size()==1&&labels(values.get(0).getDefiningClass()).contains(", value="),"Server cursor value is read directly");
    FieldReference value=values.get(0);
    FieldReference model=fields.stream().filter(f->f.getType().equals(value.getDefiningClass())).findFirst().orElseThrow();
    check(fields.stream().anyMatch(f->f.getType().equals(model.getDefiningClass())),"Cursor wrapper, cursor model and server value form a typed field chain");
    check(fields.stream().filter(f->f.getType().equals("Z")).map(Object::toString).distinct().count()==2,"Both native loading and backoff flags are read");
    Method refresh=method(BULK,"refresh");List<FieldReference> events=new ArrayList<>();
    for(Instruction i:code(refresh))if(i.getOpcode()==Opcode.SGET_OBJECT&&i instanceof ReferenceInstruction r&&r.getReference() instanceof FieldReference f)events.add(f);
    check(events.size()==1&&labels(events.get(0).getType()).contains("RefreshTimeline"),"Bootstrap retry uses the native RefreshTimeline object");
    String eventType=events.get(0).getType();
    check(calls(refresh).stream().anyMatch(r->r.getReturnType().equals("V")&&r.getParameterTypes().size()==1&&classes.get(eventType).getInterfaces().contains(r.getParameterTypes().get(0).toString())),"Refresh dispatch accepts the resolved native event interface");
    check(calls(refresh).stream().anyMatch(r->r.getDefiningClass().startsWith("Lcom/x/profile/timeline/")),"Refresh is dispatched to the selected child");
    Method tick=method(SESSION,"tick");
    check(calls(tick).stream().anyMatch(r->r.getName().contains("refresh")),"Session executes native refresh recovery");
    check(calls(tick).stream().anyMatch(r->r.getDefiningClass().endsWith("ProfileBulkPager$Network;")&&r.getName().equals("read")),"Session passes the native event identity to the pager");
    check(call(method(SESSION,"finish"),"Lapp/morphe/extension/newx/misc/NewXInAppNotification;","tryShow") || call(method(SESSION,"finish"),"Lapp/morphe/extension/newx/misc/NewXInAppNotification;","tryShowSuccess"),"Batch finish retains top banner");
    for(ClassDef cls:classes.values())if(cls.getType().startsWith("Lapp/morphe/extension/newx/misc/NewXDownloader$"))for(Method m:cls.getMethods())if(m.getName().equals("onCompleted")) {
      check(call(m,"Lapp/morphe/extension/newx/misc/NewXInAppNotification;","taskSuccess") || call(m,"Lapp/morphe/extension/newx/misc/NewXInAppNotification;","tryShowForUser") || call(m,"Lapp/morphe/extension/newx/misc/NewXInAppNotification;","tryShowSuccessForUser"),"Individual completion retains top banner (keyed task or legacy adapter)");
      check(calls(m).stream().noneMatch(r->r.getDefiningClass().equals("Landroid/widget/Toast;")||r.getName().toLowerCase(Locale.ROOT).contains("toast")),"Individual completion has no bottom toast fallback");
    }
    Map<String,String> expected=strings(source.resolve("patches/src/main/resources/addresources/values-zh-rCN/newx/strings.xml"));
    expected.putAll(strings(source.resolve("patches/src/main/resources/addresources/values-zh-rCN/shared/strings.xml")));
    Set<String> newKeys=Set.of("piko_newx_profile_bulk_network_failed","piko_newx_profile_bulk_timed_out","piko_newx_profile_bulk_access_failed","piko_newx_profile_bulk_rate_limited","piko_newx_profile_bulk_cursor_stalled","piko_newx_profile_bulk_tab_changed","piko_newx_profile_bulk_read_failed","piko_newx_profile_bulk_failure_phase","piko_newx_profile_bulk_retry_progress");
    int count=0,translated=0;
    try(ApkModule apk=ApkModule.loadApkFile(input)) {
      check(apk.getVersionCode()==312340001,"Output remains the exact X 12.34 build");
      apk.setLoadDefaultFramework(false);
      var table=apk.getTableBlock();
      for(var expectedString:expected.entrySet()) {
        boolean found=false;
        var entries=table.getEntries(null,"string",expectedString.getKey());
        while(entries.hasNext()) {Entry e=entries.next();var config=e.getResConfig();
          if("zh".equals(config.getLanguage())&&"CN".equals(config.getRegion())) {
            found=true;
            if(newKeys.contains(expectedString.getKey())) {
              if(!expectedString.getValue().equals(e.getValueAsString()))throw new AssertionError("Chinese text differs: "+expectedString.getKey());
              translated++;
            }
          }
        }
        if(!found)throw new AssertionError("Missing compiled Chinese string: "+expectedString.getKey());
        count++;
      }
    }
    check(translated==newKeys.size(),"All nine new Chinese recovery/error strings match source");
    check(count==expected.size(),"All "+count+" Chinese settings strings present in final resources");
    MessageDigest hash=MessageDigest.getInstance("SHA-256");
    try(InputStream in=new DigestInputStream(new FileInputStream(input),hash)){in.transferTo(OutputStream.nullOutputStream());}
    Map<String,Object> report=new LinkedHashMap<>();
    report.put("apk",input.getName());report.put("bytes",input.length());report.put("sha256",HexFormat.of().formatHex(hash.digest()));
    report.put("native_empty_type",empty);report.put("native_success_type",success);report.put("cursor_value",value.toString());
    report.put("refresh_event",events.get(0).toString());report.put("chinese_strings",count);report.put("new_chinese_strings",translated);
    report.put("checks",checks);report.put("passed",true);
    Files.writeString(Path.of(args[2]),json(report)+"\n");
    System.out.println("Passed "+checks.size()+" bytecode/resource checks; Chinese strings="+count);
  }
}
