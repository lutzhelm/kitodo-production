/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.services.schema;

import de.sub.goobi.config.ConfigCore;
import de.sub.goobi.export.dms.ExportDms;
import de.sub.goobi.export.download.ExportMets;
import de.sub.goobi.helper.BeanHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.ExportFileException;
import de.sub.goobi.helper.exceptions.InvalidImagesException;
import de.sub.goobi.metadaten.MetadatenImagesHelper;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.ProjectFileGroup;

import org.kitodo.services.ServiceManager;
import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.dl.VirtualFileGroup;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.fileformats.mets.MetsModsImportExport;

/**
 * Service for schema manipulations.
 */
public class SchemaService {

    private static final Logger logger = LogManager.getLogger(SchemaService.class);
    private final ServiceManager serviceManager = new ServiceManager();
    private List<DocStruct> docStructsWithoutPages = new ArrayList<>();

    /**
     * Temporal method for separate file conversion from ExportMets class
     * (method writeMetsFile).
     *
     * @param exportMets
     *            class inside method is used
     * @param metsMods
     *            MetsModsImportExport object
     * @param prefs
     *            preferences - Prefs object
     * @param process
     *            object
     * @return MetsModsImportExport object
     */
    public <T extends ExportMets> MetsModsImportExport tempConvert(Fileformat gdzfile, T exportMets,
            MetsModsImportExport metsMods, Prefs prefs, Process process)
            throws IOException, PreferencesException, TypeNotAllowedForParentException {
        URI imageFolder = serviceManager.getFileService().getImagesDirectory(process);

        /*
         * before creating mets file, change relative path to absolute -
         */
        DigitalDocument digitalDocument = gdzfile.getDigitalDocument();
        if (digitalDocument.getFileSet() == null) {
            Helper.setMeldung(process.getTitle()
                    + ": digital document does not contain images; temporarily adding them for mets file creation");
            MetadatenImagesHelper mih = new MetadatenImagesHelper(prefs, digitalDocument);
            mih.createPagination(process, null);
        }

        /*
         * get the topstruct element of the digital document depending on anchor
         * property
         */
        DocStruct topElement = digitalDocument.getLogicalDocStruct();
        if (prefs.getDocStrctTypeByName(topElement.getType().getName()).getAnchorClass() != null) {
            if (topElement.getAllChildren() == null || topElement.getAllChildren().size() == 0) {
                throw new PreferencesException(process.getTitle()
                        + ": the topstruct element is marked as anchor, but does not have any children for "
                        + "physical docstrucs");
            } else {
                topElement = topElement.getAllChildren().get(0);
            }
        }

        /*
         * if the top element does not have any image related, set them all
         */
        if (topElement.getAllToReferences("logical_physical") == null
                || topElement.getAllToReferences("logical_physical").size() == 0) {
            if (digitalDocument.getPhysicalDocStruct() != null
                    && digitalDocument.getPhysicalDocStruct().getAllChildren() != null) {
                Helper.setMeldung(process.getTitle()
                        + ": topstruct element does not have any referenced images yet; temporarily adding them "
                        + "for mets file creation");
                for (DocStruct mySeitenDocStruct : digitalDocument.getPhysicalDocStruct().getAllChildren()) {
                    topElement.addReferenceTo(mySeitenDocStruct, "logical_physical");
                }
            } else {
                if (exportMets instanceof ExportDms && ((ExportDms) exportMets).exportDmsTask != null) {
                    ((ExportDms) exportMets).exportDmsTask.setException(new RuntimeException(
                            process.getTitle() + ": could not find any referenced images, export aborted"));
                } else {
                    Helper.setFehlerMeldung(
                            process.getTitle() + ": could not find any referenced images, export aborted");
                }
                return null;
            }
        }

        for (ContentFile cf : digitalDocument.getFileSet().getAllFiles()) {
            String location = cf.getLocation();
            // If the file's location string shows no sign of any protocol,
            // use the file protocol.
            if (!location.contains("://")) {
                location = "file://" + location;
            }
            cf.setLocation(location);
        }

        metsMods.setDigitalDocument(digitalDocument);

        /*
         * wenn Filegroups definiert wurden, werden diese jetzt in die
         * Metsstruktur übernommen
         */
        // Replace all paths with the given VariableReplacer, also the file
        // group paths!
        VariableReplacer vp = new VariableReplacer(metsMods.getDigitalDocument(), prefs, process, null);
        List<ProjectFileGroup> fileGroups = process.getProject().getProjectFileGroups();

        if (fileGroups != null && fileGroups.size() > 0) {
            for (ProjectFileGroup pfg : fileGroups) {
                // check if source files exists
                if (pfg.getFolder() != null && pfg.getFolder().length() > 0) {
                    URI folder = serviceManager.getProcessService().getMethodFromName(pfg.getFolder(), process);
                    if (serviceManager.getFileService().fileExist(folder)
                            && serviceManager.getFileService().getSubUris(folder).size() > 0) {
                        metsMods.getDigitalDocument().getFileSet().addVirtualFileGroup(setVirtualFileGroup(pfg, vp));
                    }
                } else {
                    metsMods.getDigitalDocument().getFileSet().addVirtualFileGroup(setVirtualFileGroup(pfg, vp));
                }
            }
        }

        // Replace rights and digiprov entries.
        metsMods.setRightsOwner(vp.replace(process.getProject().getMetsRightsOwner()));
        metsMods.setRightsOwnerLogo(vp.replace(process.getProject().getMetsRightsOwnerLogo()));
        metsMods.setRightsOwnerSiteURL(vp.replace(process.getProject().getMetsRightsOwnerSite()));
        metsMods.setRightsOwnerContact(vp.replace(process.getProject().getMetsRightsOwnerMail()));
        metsMods.setDigiprovPresentation(vp.replace(process.getProject().getMetsDigiprovPresentation()));
        metsMods.setDigiprovReference(vp.replace(process.getProject().getMetsDigiprovReference()));
        metsMods.setDigiprovPresentationAnchor(vp.replace(process.getProject().getMetsDigiprovPresentationAnchor()));
        metsMods.setDigiprovReferenceAnchor(vp.replace(process.getProject().getMetsDigiprovReferenceAnchor()));

        metsMods.setPurlUrl(vp.replace(process.getProject().getMetsPurl()));
        metsMods.setContentIDs(vp.replace(process.getProject().getMetsContentIDs()));

        // Set mets pointers. MetsPointerPathAnchor or mptrAnchorUrl is the
        // pointer used to point to the superordinate (anchor) file, that is
        // representing a “virtual” group such as a series. Several anchors
        // pointer paths can be defined/ since it is possible to define several
        // levels of superordinate structures (such as the complete edition of
        // a daily newspaper, one year ouf of that edition, …)
        String anchorPointersToReplace = process.getProject().getMetsPointerPath();
        metsMods.setMptrUrl(null);
        for (String anchorPointerToReplace : anchorPointersToReplace.split(Project.ANCHOR_SEPARATOR)) {
            String anchorPointer = vp.replace(anchorPointerToReplace);
            metsMods.setMptrUrl(anchorPointer);
        }

        // metsPointerPathAnchor or mptrAnchorUrl is the pointer used to point
        // from the (lowest) superordinate
        // (anchor) file to the lowest level file (the non-anchor file).
        String metsPointerToReplace = process.getProject().getMetsPointerPathAnchor();
        String metsPointer = vp.replace(metsPointerToReplace);
        metsMods.setMptrAnchorUrl(metsPointer);

        if (ConfigCore.getBooleanParameter("ExportValidateImages", true)) {
            try {
                // TODO andere Dateigruppen nicht mit image Namen ersetzen
                List<URI> images = new MetadatenImagesHelper(prefs, digitalDocument).getDataFiles(process);
                List<String> imageStrings = new ArrayList<>();
                for (URI uri : images) {
                    imageStrings.add(uri.toString());
                }
                int sizeOfPagination = digitalDocument.getPhysicalDocStruct().getAllChildren().size();
                if (images.size() > 0) {
                    int sizeOfImages = images.size();
                    if (sizeOfPagination == sizeOfImages) {
                        digitalDocument.overrideContentFiles(imageStrings);
                    } else {
                        List<String> param = new ArrayList<>();
                        param.add(String.valueOf(sizeOfPagination));
                        param.add(String.valueOf(sizeOfImages));
                        Helper.setFehlerMeldung(Helper.getTranslation("imagePaginationError", param));
                        return null;
                    }
                }
            } catch (IndexOutOfBoundsException | InvalidImagesException e) {
                logger.error(e);
                return null;
            }
        } else {
            // create pagination out of virtual file names
            digitalDocument.addAllContentFiles();
        }

        metsMods.setDigitalDocument(digitalDocument);
        return metsMods;
    }

    private VirtualFileGroup setVirtualFileGroup(ProjectFileGroup projectFileGroup, VariableReplacer variableReplacer) {
        VirtualFileGroup virtualFileGroup = new VirtualFileGroup();

        virtualFileGroup.setName(projectFileGroup.getName());
        virtualFileGroup.setPathToFiles(variableReplacer.replace(projectFileGroup.getPath()));
        virtualFileGroup.setMimetype(projectFileGroup.getMimeType());
        virtualFileGroup.setFileSuffix(projectFileGroup.getSuffix());
        virtualFileGroup.setOrdinary(!projectFileGroup.isPreviewImage());

        return virtualFileGroup;
    }

    /**
     * Conversion for RUSDML.
     *
     * @param digitalDocument
     *            object
     * @param prefs
     *            object
     * @param process
     *            object
     * @param atsPpnBand as String
     */
    public void tempConvertRusdml(DigitalDocument digitalDocument, Prefs prefs, Process process, String atsPpnBand)
            throws ExportFileException, MetadataTypeNotAllowedException {

        /*
         * Run recursively through DocStruct and check the metadata
         */
        DocStruct logicalTopStruct = digitalDocument.getLogicalDocStruct();

        evaluateDocStructPages(logicalTopStruct, prefs);
        correctPathImageFiles(digitalDocument.getPhysicalDocStruct(), prefs, "./" + atsPpnBand + "_tif");
        addMissingMetadata(logicalTopStruct, prefs, process);
    }

    /**
     * Run through all structural elements recursively and assign the children's sides to the parent elements.
     *
     * @param inStruct
     *            DocStruct object
     * @param prefs
     *            object
     */
    private void evaluateDocStructPages(DocStruct inStruct, Prefs prefs)
            throws DocStructHasNoTypeException, MetadataTypeNotAllowedException {
        dropRusdmlMetadata(inStruct, prefs);
        rusdmlDropPersons(inStruct);
        maskUmlauts(inStruct);
        checkMetadata(inStruct, prefs);

        // if the Docstruct has no pictures, it is taken into the list
        if (inStruct.getAllToReferences().size() == 0 && inStruct.getType().getAnchorClass() == null) {
            docStructsWithoutPages.add(inStruct);
        }

        // run through all children of the current DocStruct
        if (inStruct.getAllChildren() != null) {
            for (DocStruct child : inStruct.getAllChildren()) {
                evaluateDocStructPages(child, prefs);
            }
        }
    }

    /**
     * Drop all unnecessary metadata of the RUSDML project.
     *
     * @param inStruct
     *            DocStruct
     * @param prefs
     *            object
     */
    private void dropRusdmlMetadata(DocStruct inStruct, Prefs prefs)
            throws DocStructHasNoTypeException, MetadataTypeNotAllowedException {
        String titleRu = "";
        String titleOther = "";
        String language = "";

        if (inStruct.getAllVisibleMetadata() != null) {
            List<Metadata> copy = new ArrayList<>(inStruct.getAllMetadata());
            for (Metadata meta : copy) {
                // now delete all unneeded metadata
                if (meta.getType().getName().equals("RUSMainTitle")) {
                    titleRu = meta.getValue();
                    inStruct.getAllMetadata().remove(meta);
                }
                if (meta.getType().getName().equals("TitleDocMain")) {
                    titleOther = meta.getValue();
                    inStruct.getAllMetadata().remove(meta);
                }

                if (meta.getType().getName().equals("DocLanguage")) {
                    meta.setValue(meta.getValue().toLowerCase());
                    language = meta.getValue();
                }

                if (meta.getType().getName().equals("RUSPublisher")) {
                    inStruct.getAllMetadata().remove(meta);
                }
                if (meta.getType().getName().equals("RUSPlaceOfPublication")) {
                    inStruct.getAllMetadata().remove(meta);
                }
                if (meta.getType().getName().equals("RUSPublicationHouse")) {
                    inStruct.getAllMetadata().remove(meta);
                }

                if (meta.getType().getName().equals("ZBLSource")) {
                    inStruct.getAllMetadata().remove(meta);
                }
                if (meta.getType().getName().equals("ZBLIntern")) {
                    inStruct.getAllMetadata().remove(meta);
                }
                if (meta.getType().getName().equals("ZBLPageNumber")) {
                    inStruct.getAllMetadata().remove(meta);
                }
                if (meta.getType().getName().equals("ZBLReviewLink")) {
                    inStruct.getAllMetadata().remove(meta);
                }
                if (meta.getType().getName().equals("ZBLReviewAuthor")) {
                    inStruct.getAllMetadata().remove(meta);
                }
                if (meta.getType().getName().equals("ZBLCita")) {
                    inStruct.getAllMetadata().remove(meta);
                }
                if (meta.getType().getName().equals("ZBLTempID")) {
                    inStruct.getAllMetadata().remove(meta);
                }

                /*
                 * the abstract of the ZBL, but only the 255 first characters
                 */
                if (meta.getType().getName().equals("ZBLAbstract")) {
                    MetadataType mdt = prefs.getMetadataTypeByName("Abstract");
                    meta.setType(mdt);
                    if (meta.getValue().length() > 255) {
                        meta.setValue(meta.getValue().substring(0, 254));
                    }
                }
            }
        }

        /*
         * nachdem alle Metadaten durchlaufen wurden, jetzt abhängig vom
         * Sprachcode den richtigen MainTitle zuweisen
         */
        MetadataType mdtOrg = prefs.getMetadataTypeByName("TitleDocMain");
        Metadata metaOrg = new Metadata(mdtOrg);
        MetadataType mdtTrans = prefs.getMetadataTypeByName("MainTitleTranslated");
        Metadata metaTrans = new Metadata(mdtTrans);
        if (language.equals("ru")) {
            metaOrg.setValue(titleRu);
            metaTrans.setValue(titleOther);
        } else {
            metaTrans.setValue(titleRu);
            metaOrg.setValue(titleOther);
        }

        if (metaOrg.getValue() != null && metaOrg.getValue().length() > 0) {
            inStruct.addMetadata(metaOrg);
        }
        if (metaTrans.getValue() != null && metaTrans.getValue().length() > 0) {
            inStruct.addMetadata(metaTrans);
        }
    }

    /**
     * Remove all unnecessary persons.
     *
     * @param inStruct
     *            DocStruct object
     */
    private void rusdmlDropPersons(DocStruct inStruct) {
        if (inStruct.getAllPersons() != null) {
            List<Person> copy = new ArrayList<>(inStruct.getAllPersons());
            for (Person copyPerson : copy) {
                if (copyPerson.getType().getName().equals("ZBLAuthor")) {
                    inStruct.getAllPersons().remove(copyPerson);
                }
            }
        }
    }

    /**
     * Modify all metadata to modify.
     *
     * @param inStruct
     *            DocStruct object
     * @param prefs
     *            object
     */
    private void checkMetadata(DocStruct inStruct, Prefs prefs) {
        if (inStruct.getType().getName().equals("Illustration")) {
            DocStructType dst = prefs.getDocStrctTypeByName("Figure");
            inStruct.setType(dst);
        }
    }

    private void correctPathImageFiles(DocStruct phys, Prefs prefs, String newValue) throws ExportFileException {
        MetadataType mdTypeForPath = prefs.getMetadataTypeByName("pathimagefiles");
        List<? extends Metadata> allMetadata = phys.getAllMetadataByType(mdTypeForPath);
        if (allMetadata.size() > 0) {
            for (Metadata meta : allMetadata) {
                meta.setValue(newValue);
            }
        } else {
            throw new ExportFileException("Export error: Image path not yet set");
        }
    }

    /**
     * Maintain the central project settings in the xml configuration.
     *
     * @param topStruct
     *            DocStruct object
     * @param prefs
     *            object
     * @param process
     *            Process object
     */
    private void addMissingMetadata(DocStruct topStruct, Prefs prefs, Process process) throws ExportFileException {
        String ppn = BeanHelper.determineWorkpieceProperty(process, "PPN digital");
        if (ppn.length() == 0) {
            throw new ExportFileException("Export error: No PPN digital present");
        }
        addMissingMetadata(topStruct, prefs, process, ppn);
    }

    /**
     * Add missing metadata for RUSDML.
     *
     * @param inTopStruct
     *            DocStruct object
     * @param prefs
     *            object
     * @param process
     *            Process object
     * @param ppn
     *            String
     */
    private void addMissingMetadata(DocStruct inTopStruct, Prefs prefs, Process process, String ppn) {
        /*
         * Get properties from the workpiece
         */
        String title = BeanHelper.determineWorkpieceProperty(process, "Haupttitel");
        String verlag = BeanHelper.determineWorkpieceProperty(process, "Verlag");
        String place = BeanHelper.determineWorkpieceProperty(process, "Erscheinungsort");
        String ISSN = BeanHelper.determineWorkpieceProperty(process, "ISSN");
        String bandNumber = BeanHelper.determineWorkpieceProperty(process, "Band");

        /*
         * Produce metadata
         */
        Metadata mdVerlag = null;
        Metadata mdPlace = null;
        Metadata mdISSN = null;
        Metadata mdPPN = null;
        Metadata mdPPNBand = null;
        Metadata mdSorting = null;
        try {
            mdVerlag = new Metadata(prefs.getMetadataTypeByName("PublisherName"));
            mdVerlag.setValue(verlag);
            mdPlace = new Metadata(prefs.getMetadataTypeByName("PlaceOfPublication"));
            mdPlace.setValue(place);
            mdISSN = new Metadata(prefs.getMetadataTypeByName("ISSN"));
            mdISSN.setValue(ISSN);
            mdPPN = new Metadata(prefs.getMetadataTypeByName("CatalogIDDigital"));
            mdPPN.setValue("PPN" + ppn);
            mdPPNBand = new Metadata(prefs.getMetadataTypeByName("CatalogIDDigital"));
            mdPPNBand.setValue("PPN" + ppn + "_" + bandNumber);
            mdSorting = new Metadata(prefs.getMetadataTypeByName("CurrentNoSorting"));
            int bandInt = Integer.parseInt(bandNumber) * 10;
            mdSorting.setValue(String.valueOf(bandInt));
        } catch (MetadataTypeNotAllowedException | NumberFormatException e) {
            logger.error(e);
        }

        /*
         * assign magazine's metadata
         */
        inTopStruct.getAllMetadataByType(prefs.getMetadataTypeByName("TitleDocMain")).get(0).setValue(title);
        try {
            inTopStruct = preventNullMetadataInsert(inTopStruct, mdVerlag);
            inTopStruct = preventNullMetadataInsert(inTopStruct, mdPlace);
            inTopStruct = preventNullMetadataInsert(inTopStruct, mdPPN);
            inTopStruct = preventNullMetadataInsert(inTopStruct, mdISSN);
        } catch (DocStructHasNoTypeException | MetadataTypeNotAllowedException e) {
            logger.error(e.getMessage());
        }

        /*
         * die Metadaten dem Band zuweisen
         */
        DocStruct structBand = inTopStruct.getAllChildren().get(0);
        if (structBand != null) {
            try {
                structBand = preventNullMetadataInsert(structBand, mdVerlag);
                structBand = preventNullMetadataInsert(structBand, mdPlace);
                structBand = preventNullMetadataInsert(structBand, mdPPNBand);
                structBand = preventNullMetadataInsert(structBand, mdSorting);
            } catch (DocStructHasNoTypeException | MetadataTypeNotAllowedException e) {
                logger.error(e.getMessage());
            }
        }
    }

    private DocStruct preventNullMetadataInsert(DocStruct docStruct, Metadata metadata) throws MetadataTypeNotAllowedException {
        if (metadata != null) {
            docStruct.addMetadata(metadata);
        }
        return docStruct;
    }

    /**
     * All metadata of a structural element are traversed and their umlauts
     * masked.
     *
     * @param inStruct
     *            DocStruct object
     */
    private void maskUmlauts(DocStruct inStruct) {
        List<Metadata> copy = inStruct.getAllMetadata();
        if (copy != null) {
            for (Metadata meta : copy) {
                maskUmlauts(meta);
            }
        }
    }

    /**
     * Remove the umlauts in the metadata.
     *
     * @param meta
     *            object
     */
    private void maskUmlauts(Metadata meta) {
        String newValue = meta.getValue();
        if (newValue == null) {
            return;
        }
        newValue = newValue.replaceAll("\\\\star", "\u002a");
        newValue = newValue.replaceAll("\\\\times", "\u00d7");
        newValue = newValue.replaceAll("\\\\div", "\u00f7");
        newValue = newValue.replaceAll("\\\\dot G", "\u0120");
        newValue = newValue.replaceAll("\\\\Gamma", "\u0393");
        newValue = newValue.replaceAll("\\\\Delta", "\u00394");
        newValue = newValue.replaceAll("\\\\Lambda", "\u039b");
        newValue = newValue.replaceAll("\\\\Sigma", "\u03a3");
        newValue = newValue.replaceAll("\\\\Omega", "\u03a9");
        newValue = newValue.replaceAll("\\\\alpha", "\u03b1");
        newValue = newValue.replaceAll("\\\\beta", "\u03b2");
        newValue = newValue.replaceAll("\\\\gamma", "\u03b3");
        newValue = newValue.replaceAll("\\\\delta", "\u002a");
        newValue = newValue.replaceAll("\\\\epsilon", "\u03b5");
        newValue = newValue.replaceAll("\\\\zeta", "\u03b6");
        newValue = newValue.replaceAll("\\\\eta", "\u03b7");
        newValue = newValue.replaceAll("\\\\theta", "\u03b8");
        newValue = newValue.replaceAll("\\\\lambda", "\u03bb");
        newValue = newValue.replaceAll("\\\\mu", "\u03bc");
        newValue = newValue.replaceAll("\\\\nu", "\u03bd");
        newValue = newValue.replaceAll("\\\\pi", "\u03c0");
        newValue = newValue.replaceAll("\\\\sigma", "\u03c3");
        newValue = newValue.replaceAll("\\\\phi", "\u03c6");
        newValue = newValue.replaceAll("\\\\omega", "\u03c9");
        newValue = newValue.replaceAll("\\\\ell", "\u2113");
        newValue = newValue.replaceAll("\\\\rightarrow", "\u2192");
        newValue = newValue.replaceAll("\\\\sim", "\u223c");
        newValue = newValue.replaceAll("\\\\le", "\u2264");
        newValue = newValue.replaceAll("\\\\ge", "\u2265");
        newValue = newValue.replaceAll("\\\\odot", "\u2299");
        newValue = newValue.replaceAll("\\\\infty", "\u221e");
        newValue = newValue.replaceAll("\\\\circ", "\u2218");
        newValue = newValue.replaceAll("\\\\dot\\{P\\}", "\u1e56");
        newValue = newValue.replaceAll("\\\\symbol\\{94\\}", "\u005e");
        newValue = newValue.replaceAll("\\\\symbol\\{126\\}", "\u007e");
        newValue = newValue.replaceAll("\\\\u g", "\u011f");
        newValue = newValue.replaceAll("\\\\AE ", "\u00c6");
        newValue = newValue.replaceAll("\\\\ae ", "\u00e6");
        newValue = newValue.replaceAll("\\\\oe ", "\u0153");
        newValue = newValue.replaceAll("\\\\OE ", "\u0152");
        newValue = newValue.replaceAll("\\\\uu ", "u");
        newValue = newValue.replaceAll("\\\\UU ", "U");
        newValue = newValue.replaceAll("\\\\Dj ", "Dj");
        newValue = newValue.replaceAll("\\\\dj ", "dj");
        newValue = newValue.replaceAll("\\\\c\\{c\\}", "\u00e7");
        newValue = newValue.replaceAll("\\\\c c", "\u00e7");
        newValue = newValue.replaceAll("\\\\c\\{C\\}", "\u00c7");
        newValue = newValue.replaceAll("\\\\c C", "\u00c7");
        // NOTE The following one only for schummling and correcting errors!
        newValue = newValue.replaceAll("\\{\\\\ss \\}", "\u00dF");
        newValue = newValue.replaceAll("\\{\\\\ss\\}", "\u00dF");
        newValue = newValue.replaceAll("\\{\\\\ss\\}", "\u00dF");
        newValue = newValue.replaceAll("\\\\ss ", "\u00df");
        newValue = newValue.replaceAll("\\\\aa ", "\u00e5");
        newValue = newValue.replaceAll("\\\\AA ", "\u00c5");
        newValue = newValue.replaceAll("\\\\dh ", "\u00f0");
        newValue = newValue.replaceAll("\\\\th ", "\u00fe");
        newValue = newValue.replaceAll("\\\\'a", "á");
        newValue = newValue.replaceAll("\\\\'A", "Á");
        newValue = newValue.replaceAll("\\\\`a", "à");
        newValue = newValue.replaceAll("\\\\`A", "À");
        newValue = newValue.replaceAll("\\\\\\^a", "â");
        newValue = newValue.replaceAll("\\\\\\^A", "Â");
        newValue = newValue.replaceAll("\\\\~a", "\u00e3");
        newValue = newValue.replaceAll("\\\\~A", "\u00c3");
        newValue = newValue.replaceAll("\\\\\\\"A", "Ä");
        newValue = newValue.replaceAll("\\\\\\\"a", "ä");
        newValue = newValue.replaceAll("\\\\'e", "é");
        newValue = newValue.replaceAll("\\\\'E", "É");
        newValue = newValue.replaceAll("\\\\`e", "è");
        newValue = newValue.replaceAll("\\\\`E", "È");
        newValue = newValue.replaceAll("\\\\\\^e", "e");
        newValue = newValue.replaceAll("\\\\\\^E", "Ê");
        newValue = newValue.replaceAll("\\\\\\\"E", "\u00cb");
        newValue = newValue.replaceAll("\\\\\\\"e", "\u00eb");
        newValue = newValue.replaceAll("\\\\'i", "í");
        newValue = newValue.replaceAll("\\\\'I", "Í");
        newValue = newValue.replaceAll("\\\\`i", "ì");
        newValue = newValue.replaceAll("\\\\`I", "Ì");
        newValue = newValue.replaceAll("\\\\\\^i", "î");
        newValue = newValue.replaceAll("\\\\\\^I", "Î");
        newValue = newValue.replaceAll("\\\\\\\"I", "\u00cf");
        newValue = newValue.replaceAll("\\\\\\\"i", "\u00ef");
        newValue = newValue.replaceAll("\\\\~n", "\u00f1");
        newValue = newValue.replaceAll("\\\\~N", "\u00d1");
        newValue = newValue.replaceAll("\\\\'o", "ó");
        newValue = newValue.replaceAll("\\\\'O", "Ó");
        newValue = newValue.replaceAll("\\\\`o", "ò");
        newValue = newValue.replaceAll("\\\\`O", "Ò");
        newValue = newValue.replaceAll("\\\\\\^o", "ô");
        newValue = newValue.replaceAll("\\\\\\^O", "Ô");
        newValue = newValue.replaceAll("\\\\~o", "\u00f5");
        newValue = newValue.replaceAll("\\\\~O", "\u00d5");
        newValue = newValue.replaceAll("\\\\\\\"O", "Ö");
        newValue = newValue.replaceAll("\\\\\\\"o", "ö");
        newValue = newValue.replaceAll("\\\\'u", "ú");
        newValue = newValue.replaceAll("\\\\'U", "Ú");
        newValue = newValue.replaceAll("\\\\`u", "ù");
        newValue = newValue.replaceAll("\\\\`U", "Ù");
        newValue = newValue.replaceAll("\\\\\\^u", "û");
        newValue = newValue.replaceAll("\\\\\\^U", "Û");
        newValue = newValue.replaceAll("\\\\\"U", "Ü");
        newValue = newValue.replaceAll("\\\\\"u", "ü");
        newValue = newValue.replaceAll("\\\\'y", "ý");
        newValue = newValue.replaceAll("\\\\'Y", "Ý");
        newValue = newValue.replaceAll("\\\\\\\"y", "\u00ff");
        newValue = newValue.replaceAll("\\\\H ", "\"");
        newValue = newValue.replaceAll("\\\\O", "\u00d8");
        newValue = newValue.replaceAll("\\\\o", "\u00f8");
        newValue = newValue.replaceAll("\\\\'C", "\u0106");
        newValue = newValue.replaceAll("\\\\'c", "\u0107");
        newValue = newValue.replaceAll("\\\\v C", "\u010c");
        newValue = newValue.replaceAll("\\\\v c", "\u010d");
        newValue = newValue.replaceAll("\\\\v S", "\u0160");
        newValue = newValue.replaceAll("\\\\v s", "\u0161");
        newValue = newValue.replaceAll("\\\\v Z", "\u017d");
        newValue = newValue.replaceAll("\\\\v z", "\u017e");
        newValue = newValue.replaceAll("\\\\v r", "\u0159");
        newValue = newValue.replaceAll("\\\\'s", "\u015b");
        newValue = newValue.replaceAll("\\\\'S", "\u015a");
        newValue = newValue.replaceAll("\\\\L", "\u0141");
        newValue = newValue.replaceAll("\\\\l", "\u0142");
        newValue = newValue.replaceAll("\\\\'N", "\u0143");
        newValue = newValue.replaceAll("\\\\'n", "\u0144");
        newValue = newValue.replaceAll("\\\\'t", "\u0165");
        newValue = newValue.replaceAll("\\\\=u", "\u016b");
        newValue = newValue.replaceAll("\\\\'z", "\u017a");
        newValue = newValue.replaceAll("\\\\.Z", "\u017b");
        newValue = newValue.replaceAll("\\\\.z", "\u017c");
        newValue = newValue.replaceAll("\\\\#", "\u0023");
        newValue = newValue.replaceAll("\\\\%", "\u0025");
        newValue = newValue.replaceAll("\\\\_", "\u005f");
        newValue = newValue.replaceAll("\\\\~ ", " ");
        newValue = newValue.replaceAll("\\\\=", "");

        meta.setValue(newValue);
    }
}
