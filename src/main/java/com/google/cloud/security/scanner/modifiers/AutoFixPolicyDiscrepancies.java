package com.google.cloud.security.scanner.modifiers;

import com.google.cloud.dataflow.sdk.transforms.DoFn;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.security.scanner.actions.modifiers.TagStateWithSource.StateSource;
import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import java.util.Map;

public class AutoFixPolicyDiscrepancies
    extends DoFn<KV<GCPResource, KV<StateSource, GCPResourceState>>,
        KV<GCPResource, Map<StateSource, GCPResourceState>>> {

  @Override
  public void processElement(ProcessContext arg0) throws Exception {
    // TODO(carise): Auto-generated method stub

  }
}

