/**   
 * License Agreement for OpenSearchServer
 *
 * Copyright (C) 2008-2012 Emmanuel Keller / Jaeksoft
 * 
 * http://www.open-search-server.com
 * 
 * This file is part of OpenSearchServer.
 *
 * OpenSearchServer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * OpenSearchServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with OpenSearchServer. 
 *  If not, see <http://www.gnu.org/licenses/>.
 **/

package com.jaeksoft.searchlib.cache;

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.search.FieldCache.StringIndex;

import com.jaeksoft.searchlib.function.expression.SyntaxError;
import com.jaeksoft.searchlib.index.FieldContentCacheKey;
import com.jaeksoft.searchlib.index.IndexConfig;
import com.jaeksoft.searchlib.index.ReaderLocal;
import com.jaeksoft.searchlib.query.ParseException;
import com.jaeksoft.searchlib.schema.Field;
import com.jaeksoft.searchlib.schema.FieldList;
import com.jaeksoft.searchlib.schema.FieldValue;
import com.jaeksoft.searchlib.schema.FieldValueItem;
import com.jaeksoft.searchlib.schema.FieldValueOriginEnum;

public class FieldCache extends
		LRUCache<FieldContentCacheKey, FieldValueItem[]> {

	private IndexConfig indexConfig;

	public FieldCache(IndexConfig indexConfig) {
		super("Field cache", indexConfig.getFieldCache());
		this.indexConfig = indexConfig;
	}

	public FieldList<FieldValue> get(ReaderLocal reader, int docId,
			FieldList<Field> fieldList) throws IOException, ParseException,
			SyntaxError {
		FieldList<FieldValue> documentFields = new FieldList<FieldValue>();
		FieldList<Field> storeField = new FieldList<Field>();
		FieldList<Field> indexedField = new FieldList<Field>();
		FieldList<Field> vectorField = new FieldList<Field>();
		FieldList<Field> missingField = new FieldList<Field>();

		// Getting available fields in the cache
		for (Field field : fieldList) {
			FieldContentCacheKey key = new FieldContentCacheKey(
					field.getName(), docId);
			FieldValueItem[] values = getAndPromote(key);
			if (values != null)
				documentFields.add(new FieldValue(field, values));
			else
				storeField.add(field);
		}

		// Check missing fields from store
		if (storeField.size() > 0) {
			Document document = reader.getDocFields(docId, storeField);
			for (Field field : storeField) {
				String fieldName = field.getName();
				Fieldable[] fieldables = document.getFieldables(fieldName);
				if (fieldables != null && fieldables.length > 0) {
					FieldValueItem[] valueItems = FieldValueItem
							.buildArray(fieldables);
					put(documentFields, field, fieldName, docId, valueItems);
				} else
					indexedField.add(field);
			}
		}

		// Check missing fields from StringIndex
		if (indexedField.size() > 0) {
			for (Field field : indexedField) {
				String fieldName = field.getName();
				StringIndex stringIndex = reader.getStringIndex(fieldName);
				if (stringIndex != null) {
					String term = stringIndex.lookup[stringIndex.order[docId]];
					if (term != null) {
						FieldValueItem[] valueItems = FieldValueItem
								.buildArray(FieldValueOriginEnum.STRING_INDEX,
										term);
						put(documentFields, field, fieldName, docId, valueItems);
						continue;
					}
				}
				vectorField.add(field);
			}
		}

		// Check missing fields from vector
		if (vectorField.size() > 0) {
			for (Field field : vectorField) {
				String fieldName = field.getName();
				TermFreqVector tfv = reader.getTermFreqVector(docId, fieldName);
				if (tfv != null) {
					FieldValueItem[] valueItems = FieldValueItem.buildArray(
							FieldValueOriginEnum.TERM_VECTOR, tfv.getTerms());
					put(documentFields, field, fieldName, docId, valueItems);
				} else
					missingField.add(field);
			}
		}

		if (missingField.size() > 0)
			for (Field field : missingField)
				documentFields.add(new FieldValue(field));

		return documentFields;
	}

	private void put(FieldList<FieldValue> documentFields, Field field,
			String fieldName, int docId, FieldValueItem[] valueItems) {
		documentFields.add(new FieldValue(field, valueItems));
		FieldContentCacheKey key = new FieldContentCacheKey(fieldName, docId);
		put(key, valueItems);
	}

	@Override
	public void setMaxSize(int newMaxSize) {
		super.setMaxSize(newMaxSize);
		indexConfig.setFieldCache(newMaxSize);
	}
}