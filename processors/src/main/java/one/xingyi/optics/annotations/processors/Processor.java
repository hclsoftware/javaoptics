package one.xingyi.optics.annotations.processors;

import one.xingyi.optics.annotations.Optics;
import one.xingyi.optics.annotations.serialise.IAnnotationProcessorStore;
import org.stringtemplate.v4.STGroupFile;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@SupportedAnnotationTypes("one.xingyi.optics.annotations.Optics")
@SupportedSourceVersion(SourceVersion.RELEASE_16)
public class Processor extends AbstractProcessor {

    private Messager messager;
    private ProcessingEnvironment pEnv;
    private Filer filer;


    protected void log(String message) {
        messager.printMessage(javax.tools.Diagnostic.Kind.NOTE, message);
    }

    private STGroupFile stringTemplate;
    protected IAnnotationProcessorStore<WithDebug<PackageAndClass>, RecordedTraversals> store;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        messager = processingEnv.getMessager();
        this.pEnv = processingEnv;
        this.filer = pEnv.getFiler();

        log("Init  Optics annotations");
        stringTemplate = new STGroupFile("record.stg");
        this.store = IAnnotationProcessorStore.<WithDebug<PackageAndClass>, RecordedTraversals>defaultStore(filer, WithDebug::t, "optics",
                RecordedTraversals.parse, RecordedTraversals.printer, this::log, WithDebug::debug);
    }


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        log("Processing Optics annotations" + annotations.size());
        for (TypeElement annotation : annotations) {
            if (annotation.getQualifiedName().toString().equals(Optics.class.getName())) {
                try {
                    log("Processing Optics annotation " + annotation);
                    processOpticsAnnotation(roundEnv, annotation);
                } catch (IOException e) {
                    log("Error processing Optics annotation " + annotation + " " + e.getClass() + "/" + e.getMessage());
                }
            }
            return true;
        }
        return false;
    }

    public PackageAndClass opticsClassName(RecordOpticsWithTraversals details) {
        return new PackageAndClass(null, details.getPackageName(), details.getClassName() + "Optics");
    }

    Stream<RecordOpticsDetails> fromAnnotation(RoundEnvironment roundEnv, TypeElement annotation) {
        annotation.getTypeParameters().forEach(t -> log("Type parameter " + t));
        return roundEnv.getElementsAnnotatedWith(annotation).stream().map(RecordOpticsDetails.fromElement(this::log));
    }

    private void processOpticsAnnotation(RoundEnvironment roundEnv, TypeElement annotation) throws IOException {
        List<RecordOpticsDetails> recordOpticsDetails = fromAnnotation(roundEnv, annotation).toList();
        for (var rop : recordOpticsDetails) {
            var recorded = new RecordedTraversals(rop.isDebug(), rop.getFieldDetails().stream().filter(f -> f.containedFieldType != null)
                    .map(f -> new NameAndType(f.name, f.getContainedFieldType())).toList());
            store.store(WithDebug.of(rop.getPackageAndClass(), rop.isDebug()), recorded);
        }

        List<RecordOpticsWithTraversals> traversals = Utils.map(recordOpticsDetails, rod -> {
            if (rod.isDebug()) log("Processing " + rod);
            var withTraversals = RecordOpticsWithTraversals.from(store, rod, this::log);
            if (rod.isDebug()) log("  --  " + withTraversals);
            return withTraversals;
        });
        Utils.map(traversals, this::makeRecordOpticsFileDefn).forEach(this::makeSourceFile);
    }

    private FileDefn makeRecordOpticsFileDefn(RecordOpticsWithTraversals d) {
        String rendered = render(d, "recordOptic");

        var className = opticsClassName(d);
        return new FileDefn(className, rendered);
    }

    private void makeSourceFile(FileDefn fileDefn) {
        try {
            String sourceFile = fileDefn.clazz().getString();
            log("Making source file " + sourceFile);
            JavaFileObject builderFile = filer.createSourceFile(sourceFile);
            try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
                out.println(fileDefn.content());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void makeRecordFile(FileDefn fileDefn) {
        try {
            FileObject builderFile = filer.createResource(StandardLocation.SOURCE_OUTPUT,
                    fileDefn.clazz().getPackageName(), fileDefn.clazz().getClassName());
            try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
                out.println(fileDefn.content());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    String render(RecordOpticsWithTraversals details, String templateName) {
        List<TraversalPathPart> allRecordsAndFields = details.getTraversalDetails().stream().flatMap(td -> td.getPath().stream()).distinct().toList();
        var record = stringTemplate.getInstanceOf(templateName);
        record.add("details", details);
        record.add("allRecordsAndFields", allRecordsAndFields);
        return record.render();
    }

}