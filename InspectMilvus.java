import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.common.IndexParam;
import java.lang.reflect.*;
public class InspectMilvus {
  public static void main(String[] args) {
    System.out.println("FloatVec constructors:");
    for (Constructor<?> c : FloatVec.class.getDeclaredConstructors()) System.out.println(c);
    System.out.println("CreateCollectionReq builder methods:");
    for (Method m : CreateCollectionReq.CreateCollectionReqBuilder.class.getDeclaredMethods()) {
      if (m.getName().equals("metricType") || m.getName().equals("consistencyLevel")) System.out.println(m);
    }
    System.out.println("IndexParam metricType method:");
    for (Method m : IndexParam.IndexParamBuilder.class.getDeclaredMethods()) {
      if (m.getName().equals("metricType")) System.out.println(m);
    }
  }
}
