/* Copyright the original author or authors.
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
package be.cytomine.apps.iris

import be.cytomine.apps.iris.model.IRISImage
import grails.converters.JSON
import org.codehaus.groovy.grails.web.json.JSONElement
import org.json.simple.JSONObject

import be.cytomine.client.Cytomine
import be.cytomine.client.CytomineException
import be.cytomine.client.collections.AnnotationCollection
import be.cytomine.client.models.Annotation
import be.cytomine.client.models.Ontology

import org.apache.log4j.Logger
import org.json.simple.parser.JSONParser

import java.awt.image.BufferedImage
import java.awt.image.Raster
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


/**
 * Utility class for the Cytomine IRIS application.
 * @author Philipp Kainz
 * @since 0.1
 */
class Utils {

    Logger log = Logger.getLogger(Utils.class)

    /**
     * Computes the annotation progress of a user in an image. Each
     * annotation has to have at least one term assigned by the user,
     * otherwise this does not attribute to the progress.
     *
     * @param cytomine a Cytomine instance
     * @param projectID the cytomine project id
     * @param imageID the cytomine image id
     * @param userID the cytomine user id
     * @return a JSONObject (map) which contains progress information for the user
     */
    JSONObject getUserProgress(Cytomine cytomine, long projectID, long imageID, long userID) {
        JSONObject jsonResult = new JSONObject()
        // clone the object and retrieve every object without pagination
        Cytomine cm = new Cytomine(cytomine.getHost(), cytomine.getPublicKey(), cytomine.getPrivateKey())

        int totalAnnotations = 0
        int labeledAnnotations = 0

        // define the filter for the query
        Map<String, String> filters = new HashMap<String, String>()
        filters.put("project", String.valueOf(projectID))
        filters.put("image", String.valueOf(imageID))

        // get the annotations of this image (as batch, causes 1 access per image)
        AnnotationCollection annotations = cm.getAnnotations(filters)

        // total annotations in a given image
        totalAnnotations = annotations.size();

        // count the annotations per user
        for (int i = 0; i < totalAnnotations; i++) {
            Annotation annotation = annotations.get(i)
            // grab all terms from all users for the current annotation
            List userByTermList = annotation.getList("userByTerm");
            for (assignment in userByTermList) {
                List userList = assignment.get("user").toList()

                // if the user has assigned a label to this annotation, increase the counter
                if (userID in userList) {
                    labeledAnnotations++
                }
            }
        }

        jsonResult.put("projectID", projectID)
        jsonResult.put("imageID", imageID)
        jsonResult.put("labeledAnnotations", labeledAnnotations)
        jsonResult.put("numberOfAnnotations", totalAnnotations)
        // compute the progress in percent
        int userProgress = (totalAnnotations == 0 ? 0 : (int) ((labeledAnnotations / totalAnnotations) * 100));
        jsonResult.put("userProgress", userProgress)

        // return the json result
        return jsonResult;
    }

    /**
     * Flattens an ontology, which may be hierarchical.
     *
     * @param ontology
     * @return a list of JSONObjects
     */
    List<JSONObject> flattenOntology(Ontology ontology) {
        // perform recursion and flatten the hierarchy
        JSONObject root = ontology.getAttr();
        List flatHierarchy = new ArrayList<JSONObject>();

        // build a lookup table for each term in the annotation
        Map<Long, Object> dict = new HashMap<Long, Object>();

        // pass the root node
        flatHelper(root, dict, flatHierarchy);

        return flatHierarchy;
    }

    /**
     * Flattens a hierarchy, where the parent node is an attribute of each child node.
     *
     * @param node the node (mostly root node)
     * @param dict the dictionary to map from
     * @param flatHierarchy the flat hierarchy to write to
     */
    private void flatHelper(JSONObject node, Map<Long, Object> dict, List flatHierarchy) {
        // get the node's children
        List childrenList = node.get("children").toList()

        // recurse through the children
        for (child in childrenList) {
            // put each node to the dictionary
            dict.put(Long.valueOf(child.get("id")), child);

            if (child.get("isFolder")) {
                flatHelper(child, dict, flatHierarchy);
            } else {
                String parentName = "root";

                if (child.get("parent") != null) {
                    // these are the non-root elements
                    parentName = dict.get(Long.valueOf(child.get("parent"))).get("name")
                }

                // add the child to the flat ontology
                child.put("parentName", parentName)
                flatHierarchy.add(child);
            }
        }
    }

    /**
     * Converts a domain object to JSON format using the custom marshaller classes.
     * @param object
     * @return the JSONElement object
     */
    JSONElement modelToJSON(def object) {
        return JSON.parse((object as JSON).toString())
    }

    JSONObject toJSONObject(def object) {
        JSONParser parser = new JSONParser()
        StringReader objectReader = new StringReader((object as JSON).toString())
        JSONObject result = (JSONObject) parser.parse(objectReader)
        if (result.isEmpty())
            return null
        else
            return result
    }

    /**
     * Gets the predecessor annotation from a collection.
     *
     * @param annotations the collection
     * @param currentIndex the current index
     * @return
     * @throws IndexOutOfBoundsException if the current index is already the beginning of the collection
     */
    Annotation getPredecessor(
            AnnotationCollection annotations,
            int currentIndex)
            throws IndexOutOfBoundsException {
        return annotations.get(currentIndex - 1)
    }

    /**
     * Gets the successor annotation from a collection.
     *
     * @param annotations the collection
     * @param currentIndex the current index
     * @return
     * @throws IndexOutOfBoundsException if the current index is already the end of the collection
     */
    Annotation getSuccessor(
            AnnotationCollection annotations,
            int currentIndex)
            throws IndexOutOfBoundsException {
        return annotations.get(currentIndex + 1)
    }

    /**
     * Compute split indices for a list of objects.
     *
     * @param theList
     * @param nParts
     * @return an array constisting of split indices
     */
    def getSplitIndices(def theList, int nParts) {
        def nItems = theList.size()
        def splitIndices;

        if (nItems == 0) {
            splitIndices = []
        } else if (nParts == 1 || nItems == 1) {
            splitIndices = [nItems - 1]
        } else {
            int maxSplits = nParts - 1;
            // determine the number of parts
            if (nItems <= nParts) {
                // limit the number of parts by the number of items
                nParts = nItems
                maxSplits = nParts
            }

            int nElementsPerPart = Math.round(nItems / nParts)
            //assert nParts == new Double(Math.round(nItems / nElementsPerPart)).intValue()

            // split the list in subparts
            splitIndices = (1..maxSplits).collect { (it * nElementsPerPart) - 1 }
            log.debug("max elements per part: " + nElementsPerPart + ", parts: " + nParts + ", maxSplits: " + maxSplits)
        }
        log.debug("      --> Split indices: " + splitIndices)

        return splitIndices
    }

    /**
     * Resolve a CytomineException object to JSON data, which can be rendered to the client.
     * The status code is inherently stored in the CytomineException object.
     *
     * @param e the exception
     * @return a JSONObject
     */
    JSONObject resolveCytomineException(CytomineException e) {
        JSONObject errorMsg = new JSONObject()
        errorMsg.putAt("class", CytomineException.class.getName())
        errorMsg.putAt("error", new JSONObject())
        org.codehaus.groovy.grails.web.json.JSONObject msgObj
        String msg
        try {
            msgObj = new org.codehaus.groovy.grails.web.json.JSONObject(e.toString().replace(e.httpCode + " ", ""))
            msg = msgObj.getString("message")
        } catch (Exception ex) {
            msg = e.toString().replace(e.httpCode + " ", "")
        }
        errorMsg.getAt("error").putAt("message", msg)
        errorMsg.getAt("error").putAt("status", e.httpCode)
        return errorMsg
    }

    /**
     * Resolve an Exception object to a HTTP status code which can be rendered to the client as JSON.
     * @param e the exception
     * @param httpCode the status code
     * @return a JSONObject
     */
    JSONObject resolveException(Exception e, int httpCode) {
        JSONObject errorMsg = new JSONObject()
        errorMsg.putAt("class", e.getClass().getName())
        org.codehaus.groovy.grails.web.json.JSONObject errorObj
        try {
            String msg = e.getMessage() == null ? "The requested operation cannot be performed." : e.getMessage()
            errorObj = new org.codehaus.groovy.grails.web.json.JSONObject("{ status : " +
                    httpCode + ", message : \"" + msg + "\" }")
        } catch (Exception ex) {
            errorObj = new org.codehaus.groovy.grails.web.json.JSONObject("{ status : " +
                    httpCode + ", message : \"The requested operation cannot be performed.\" }")
        }
        errorMsg.putAt("error", errorObj)
        return errorMsg
    }

    /**
     * Makes a deep copy of an object.
     * @param orig the original object
     * @return a deep copy of the original object
     */
    def deepcopy(def orig) {
        def bos = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(bos)
        oos.writeObject(orig); oos.flush()
        def bin = new ByteArrayInputStream(bos.toByteArray())
        def ois = new ObjectInputStream(bin)
        return ois.readObject()
    }

    /**
     * Sorts a list of Cytomine users ascending by
     * <ul>
     *     <li>lastname
     *     <li>firstname
     * </ul>
     * @param users the list of Cytomine users
     * @return sorted list
     */
    List sortUsersAsc(def users) {
        // sort the users by lastname, firstname asc
        users.sort { x, y ->
            if (x.get("lastname") == y.get("lastname")) {
                x.get("firstname") <=> y.get("firstname")
            } else {
                x.get("lastname") <=> y.get("lastname")
            }
        }
        return users
    }

    /**
     * Sort an array of agreement statistics.
     * Primarily: ratio descending, secondarily: termName ascending
     * @param agreements list of agreement statistics
     * @return sorted list
     */
    List sortAgreementsDesc(def agreements) {

        if (agreements == null){
            return null
        }

        agreements.sort { x, y ->
            if (y.get("ratio") == x.get("ratio")) {
                x.get("termName") <=> y.get("termName")
            } else {
                y.get("ratio") <=> x.get("ratio")
            }
        }
    }

    /**
     * Creates a flattened ZIP file recursively from the given srcDir.
     * @param srcDir
     * @param zipFile
     * @return
     * @throws Exception
     */
    def createFlatZIPFromDirectory(String srcDir, String zipFile) throws Exception {

        try {
            FileOutputStream fos = new FileOutputStream(zipFile);
            ZipOutputStream zos = new ZipOutputStream(fos);
            File srcFile = new File(srcDir);
            addDirToArchive(zos, srcFile);

            // close the ZipOutputStream
            zos.close();

        }
        catch (IOException ioe) {
            log.error("Error creating ZIP file.", ioe);
        }

        return zipFile;
    }

    /**
     * Adds a directory to a ZIP archive.
     * @param zos
     * @param srcFile
     * @throws Exception
     */
    def addDirToArchive(ZipOutputStream zos, File srcFile) throws Exception {

        File[] files = srcFile.listFiles();
        log.debug("Adding directory: " + srcFile.getName());

        for (int i = 0; i < files.length; i++) {

            // if the file is directory, use recursion
            if (files[i].isDirectory()) {
                addDirToArchive(zos, files[i]);
                continue;
            }

            try {

                log.debug("Adding file: " + files[i].getName());

                // create byte buffer
                byte[] buffer = new byte[1024];

                FileInputStream fis = new FileInputStream(files[i]);
                zos.putNextEntry(new ZipEntry(files[i].getName()));

                int length;
                while ((length = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, length);
                }
                zos.closeEntry();

                // close the InputStream
                fis.close();

            } catch (IOException ioe) {
                log.error("Error adding file to ZIP archive.", ioe);
            }

        }
    }


    // TODO currently unused
//    def annotationsToCSV(def annotations, def hdrs, def terms, def users, def intermediate, def imageDimensions, Cytomine cytomine){
//
//        int total = annotations.size();
//        def lines = []
//        lines.add(hdrs)
//
//        // loop through all annotations
//        for (int i=0; i < total; i++){
//
//            def annotation = annotations[i]
//
//            // annotation info
//            def line = []
//            line.add(i+1)
//            line.add(annotation.getId())
//            //line.add('') // leave image empty
//            projectid = annotation.get("project")
//            imageid = annotation.get("image")
//            //line.add(annotation.get("cropURL")) // Cytomine url
//            //line.add(irisPATH+"/index.html#/project/"+projectid+"/image/"+imageid+"/label/"+annotation.getId()) // plain url
//            line.add("=HYPERLINK(\""+irisPATH+"/index.html#/project/"+projectid+"/image/"+imageid+"/label/"+annotation.getId()+"\")") // XLS hyperlink
//
//            line.add((annotation in intermediate)?'X':'') // if it is an intermediate label, print an X
//
//            // grab all terms from all users for the current annotation
//            List userByTermList = annotation.getList("userByTerm")
//
//            for (u in users) {
//                String termname = ''
//                for (assignment in userByTermList){
//                    //println currentUser.get("id") + ", " + assignment.get("user")
//                    if (u.get("id") in assignment.get("user")){
//                        termname = terms.list.find{it.id == assignment.get("term")}.get("name")
//                    }
//                }
//                // add the user's choice
//                line.add(termname)
//            }
//
//            // WINDOW URL
//            double window_offset = (1.0d*window_size/2)
//            double cX = annotation.get("x")
//            double cY = annotation.get("y")
//
//            int minX_window = Math.round(cX - window_offset)
//            // tricky: get the correct Y coordinates of the center
//            int minY_window = Math.round((imageDimensions[annotation.get("image")][1]-cY) - window_offset)
//
//            int width = window_size
//            int height = window_size
//
//            // link to download the window around the annotation center
//            String window_url = cytomine.host + "/api/imageinstance/" + annotation.get("image") + "/window-"+minX_window+"-"+minY_window+"-"+width+"-"+height+".png"
//
//            // add the window retrieve url (tile)
//            line.add(window_url)
//
//            //println line
//
//            // add the line to the list
//            lines.add(line)
//        }
//
//        return lines
//    }

    /**
     * Function that checks whether one of the userIDs in the parameter list
     * has assigned any term to the annotation.
     * @param userIDs
     * @param annotation
     * @return true, if so, false otherwise
     */
    def atLeastOneUserAssignedAnyTerm(def userIDs, def annotation){

        def userByTermList = annotation.getList('userByTerm')

        // loop through all assigned terms
        for (assignment in userByTermList){
            def a_userIDs = assignment.get('user')
            // make intersection of user IDs
            def commons = userIDs.intersect(a_userIDs)
            // if there is a non-empty intersection of userIDs, then someone assigned a term
            if (!commons.isEmpty()){
                return true
            }
        }

        // otherwise, no query user assigned any term
        return false
    }

    /**
     * Removes the alpha channel, if any present and returns the image with the
     * number of original color bands.
     * <p>
     * If the image does not have an alpha channel, it is returned unchanged.
     *
     * @param image a BufferedImage
     * @return a BufferedImage with alpha channel removed
     * @throws IllegalArgumentException
     *             if the image is <code>null</code>
     */
    def removeAlphaChannel(BufferedImage bi){
        if (bi.getColorModel().hasAlpha()){
            log.debug("Removing alpha channel")

            int numColorComponents = bi.getColorModel().getNumColorComponents()
            def bands = new int[numColorComponents]
            for (int i = 0; i < numColorComponents; i++)
                bands[i] = i;

            // create compatible raster
            Raster newRaster = bi.getData().createChild(0, 0, bi.getWidth(), bi.getHeight(), 0, 0, bands)

            // create new image
            BufferedImage bi2 = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_RGB)

            // set the raster to the new image
            bi2.setData(newRaster)
            return bi2
        } else {
            return bi
        }
    }
}
