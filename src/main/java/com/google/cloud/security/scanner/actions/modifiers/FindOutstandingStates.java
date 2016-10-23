package com.google.cloud.security.scanner.actions.modifiers;

import com.google.cloud.dataflow.sdk.transforms.DoFn;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.dataflow.sdk.values.PCollectionView;
import com.google.cloud.security.scanner.actions.modifiers.TagStateWithSource.StateSource;
import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.logging.Logger;

public class FindOutstandingStates
  extends DoFn<KV<GCPResource, KV<StateSource, GCPResourceState>>, String> {

  private static Logger LOG = Logger.getLogger(FindOutstandingStates.class.getName());

  private PCollectionView<Map<GCPResource, KV<StateSource, GCPResourceState>>> view;

  public FindOutstandingStates(
      PCollectionView<Map<GCPResource, KV<StateSource, GCPResourceState>>> view) {
    this.view = view;
  }

  @Override
  public void processElement(ProcessContext context)
      throws IOException, GeneralSecurityException, IllegalArgumentException {
    // the project
    GCPResource resource = context.element().getKey();
    // the project's policies
    KV<StateSource, GCPResourceState> mainValue = context.element().getValue();
    LOG.info("State=" + mainValue.getKey().toString() + ",Resource=" + resource.getId());

    if (!context.sideInput(this.view).containsKey(resource)) {
      LOG.info("Resource " + resource + " not found");
      context.output(mainValue.getKey().toString() + ":" + resource.toString());
    }
  }
}