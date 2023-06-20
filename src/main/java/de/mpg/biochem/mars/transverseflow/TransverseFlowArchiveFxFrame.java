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

import com.fasterxml.jackson.core.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.scijava.Context;

import de.mpg.biochem.mars.fx.bdv.MarsBdvCard;
import de.mpg.biochem.mars.fx.bdv.MarsBdvFrame;
import de.mpg.biochem.mars.fx.molecule.AbstractMoleculeArchiveFxFrame;
import de.mpg.biochem.mars.fx.molecule.DefaultMarsMetadataTab;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveIndex;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;

public class TransverseFlowArchiveFxFrame extends
	AbstractMoleculeArchiveFxFrame<DefaultMarsMetadataTab, TransverseFlowTab>
{

	public TransverseFlowArchiveFxFrame(
		MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive,
		final Context context)
	{
		super(archive, context);
	}

	@Override
	public DefaultMarsMetadataTab createImageMetadataTab(final Context context) {
		return new DefaultMarsMetadataTab(context);
	}

	@Override
	public TransverseFlowTab createMoleculesTab(final Context context) {
		return new TransverseFlowTab(context);
	}

	@Override
	public MarsBdvFrame createMarsBdvFrame(boolean useVolatile) {
		List<MarsBdvCard> cards = new ArrayList<MarsBdvCard>();
		TransverseFlowCard card = new TransverseFlowCard();
		context.inject(card);
		card.setArchive(archive);
		card.initialize();
		cards.add(card);
		return new MarsBdvFrame(archive, moleculesTab.getSelectedMolecule(),
			imageMetadataTab.getSelectedMetadata(), useVolatile, cards, context);
	}

	@Override
	public MarsBdvFrame createMarsBdvFrame(JsonParser jParser,
		boolean useVolatile)
	{
		try {
			return new MarsBdvFrame(jParser, archive, moleculesTab
				.getSelectedMolecule(), imageMetadataTab.getSelectedMetadata(), useVolatile, context);
		}
		catch (IOException e) {
			// have a nice error dialog show up to alert the user there is an issue.

			// Results frame with defaults
			return createMarsBdvFrame(useVolatile);
		}
	}
}
