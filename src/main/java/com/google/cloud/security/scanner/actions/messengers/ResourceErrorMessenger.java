package com.google.cloud.security.scanner.actions.messengers;

import com.google.cloud.dataflow.sdk.transforms.DoFn;
import com.google.cloud.security.scanner.primitives.GCPResourceErrorInfo;

public class ResourceErrorMessenger
    extends DoFn<GCPResourceErrorInfo, String> {

  @Override
  public void processElement(ProcessContext context)
      throws Exception {
    GCPResourceErrorInfo errorInfo = context.element();
    context.output(errorInfo.toString());
  }

}
