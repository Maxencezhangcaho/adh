/*
 * Copyright 2010 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.typography.font.sfntly.table.opentype;

import com.google.typography.font.sfntly.data.ReadableFontData;
import com.google.typography.font.sfntly.table.opentype.component.GsubLookupType;

public class ExtensionSubst extends SubstSubtable {

  private static final int LOOKUP_TYPE_OFFSET = 0;
  private static final int LOOKUP_OFFSET_OFFSET = 2;

  final GsubLookupType lookupType;
  final int lookupOffset;

  ExtensionSubst(ReadableFontData data, int base, boolean dataIsCanonical) {
    super(data, base, dataIsCanonical);
    if (format != 1) {
      throw new IllegalArgumentException("illegal extension format " + format);
    }
    lookupType = GsubLookupType.forTypeNum(
        data.readUShort(base + headerSize() + LOOKUP_TYPE_OFFSET));
    lookupOffset = data.readULongAsInt(base + headerSize() + LOOKUP_OFFSET_OFFSET);
  }

  public GsubLookupType lookupType() {
    return lookupType;
  }

  public SubstSubtable subTable() {
    ReadableFontData data = this.data.slice(lookupOffset);
    switch (lookupType) {
      case GSUB_LIGATURE:
        return new LigatureSubst(data, 0, dataIsCanonical);
      case GSUB_SINGLE:
        return new SingleSubst(data, 0, dataIsCanonical);
      case GSUB_MULTIPLE:
        return new MultipleSubst(data, 0, dataIsCanonical);
      case GSUB_ALTERNATE:
        return new AlternateSubst(data, 0, dataIsCanonical);
      case GSUB_CONTEXTUAL:
        return new ContextSubst(data, 0, dataIsCanonical);
      case GSUB_CHAINING_CONTEXTUAL:
        return new ChainContextSubst(data, 0, dataIsCanonical);
      case GSUB_REVERSE_CHAINING_CONTEXTUAL_SINGLE:
        return new ReverseChainSingleSubst(data, 0, dataIsCanonical);
      default:
        throw new IllegalArgumentException("LookupType is " + lookupType);
    }
  }

  public static class Builder extends SubstSubtable.Builder<SubstSubtable> {

    protected Builder() {
      super();
    }

    protected Builder(ReadableFontData data, boolean dataIsCanonical) {
      super(data, dataIsCanonical);
    }

    @Override public SubstSubtable subBuildTable(ReadableFontData data) {
      return null;
    }
  }

}
