/*-
 * #%L
 * Mars command and definitions for transverse flow molecule types.
 * %%
 * Copyright (C) 2023 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package de.mpg.biochem.mars.transverseflow;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.AbstractMolecule;
import de.mpg.biochem.mars.table.MarsTable;

public class TransverseFlowMolecule extends AbstractMolecule {

	private final ConcurrentMap<Integer, ReplicationForkShape> shapes;

	public TransverseFlowMolecule() {
		super();
		shapes = new ConcurrentHashMap<>();
	}

	public TransverseFlowMolecule(JsonParser jParser) throws IOException {
		super();
		shapes = new ConcurrentHashMap<>();
		fromJSON(jParser);
	}

	public TransverseFlowMolecule(String UID) {
		super(UID);
		shapes = new ConcurrentHashMap<>();
	}

	public TransverseFlowMolecule(String UID, MarsTable dataTable) {
		super(UID, dataTable);
		shapes = new ConcurrentHashMap<>();
	}

	public void putShape(int t, ReplicationForkShape shape) {
		shapes.put(t, shape);
	}

	public boolean hasShape(int t) {
		return shapes.containsKey(t);
	}

	public ReplicationForkShape getShape(int t) {
		return shapes.get(t);
	}

	public void removeShape(int t) {
		shapes.remove(t);
	}

	public Set<Integer> getShapeKeys() {
		return shapes.keySet();
	}

	/**
	 * Used to merge another TransverseFlowMolecule record into this one.
	 *
	 * @param transverseFlowMolecule TransverseFlowMolecule to merge into this one.
	 */
	public void merge(TransverseFlowMolecule transverseFlowMolecule) {
		super.merge(transverseFlowMolecule);
		transverseFlowMolecule.getShapeKeys().stream().filter(t -> !hasShape(t)).forEach(
			t -> putShape(t, transverseFlowMolecule.getShape(t)));
	}

	@Override
	protected void createIOMaps() {
		super.createIOMaps();

		setJsonField("replicationForkShapes", jGenerator -> {
			if (shapes.keySet().size() > 0) {
				jGenerator.writeArrayFieldStart("replicationForkShapes");
				for (int t : shapes.keySet()) {
					jGenerator.writeStartObject();
					jGenerator.writeNumberField("t", t);

					jGenerator.writeFieldName("replicationForkShape");
					shapes.get(t).toJSON(jGenerator);

					jGenerator.writeEndObject();
				}
				jGenerator.writeEndArray();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				int t = -1;
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					if ("t".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						t = jParser.getNumberValue().intValue();
					}

					if ("replicationForkShape".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						ReplicationForkShape shape = new ReplicationForkShape(jParser);
						if (t != -1) shapes.put(t, shape);
					}
				}
			}
		});
	}
}
