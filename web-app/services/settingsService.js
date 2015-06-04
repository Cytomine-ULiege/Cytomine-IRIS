/**
 * Created by phil on 03/06/15.
 */
var iris = angular.module("irisApp");

iris.constant("projectUsersSettingsURL", "api/settings/{projectID}/users.json");
iris.constant("projectUsersImageAccessChangeURL", "api/settings/user/{userID}/project/{projectID}/image/{imageID}/access.json");
iris.constant("imagesWithSettingsURL", "api/settings/user/{userID}/project/{projectID}/images.json");
iris.constant("projectUsersAccessChangeURL", "api/settings/user/{userID}/project/{projectID}/access.json");
iris.constant("projectUsersSyncURL", "api/admin/project/$cmProjectID/user/$cmUserID/synchronize.json");

iris.factory("settingsService", [
    "$http", "$log",
    "projectUsersSettingsURL",
    "projectUsersImageAccessChangeURL",
    "imagesWithSettingsURL",
    "projectUsersAccessChangeURL",
    "projectUsersSyncURL",
    "sessionService",
    "cytomineService",
    function($http, $log,
             projectUsersSettingsURL,
             projectUsersImageAccessChangeURL,
             imagesWithSettingsURL,
             projectUsersAccessChangeURL,
             projectUsersSyncURL,
             sessionService,
             cytomineService) {

        return {
            // get the annotations for a given project, images, terms and users
            fetchProjectUserList: function (projectID, callbackSuccess,
                                            callbackError, offset, max) {
                $log.debug("Getting project user list: " + projectID);

                // modify the parameters
                var url = cytomineService.addKeys(projectUsersSettingsURL).replace("{projectID}", projectID);

                // add pagination parameters
                if (offset) {
                    // 0 offset is counted as missing parameter and causes loading of first page
                    url += "&offset=" + offset;
                }
                if (max) {
                    url += "&max=" + max;
                }

                // execute the http get request to the IRIS server
                $http.get(url).success(function (data) {
                    // $log.debug("success on $http.get(" + url + ")");
                    //$log.debug(data);
                    if (callbackSuccess) {
                        callbackSuccess(data);
                    }
                }).error(function (data, status, headers, config) {
                    // on error log the error
//				$log.debug(callbackError)
                    if (callbackError) {
                        callbackError(data, status, headers, config);
                    }
                })
            },


            // set the image access for a given user on a project
            setImageAccess: function (projectID, imageID, userID, oldValue, newValue, settingsID, callbackSuccess,
                                      callbackError) {
                $log.debug("Posting image access settings change to IRIS: " + projectID + " - " + imageID + " - " + userID + " - " + newValue);

                // modify the parameters
                var url = cytomineService.addKeys(projectUsersImageAccessChangeURL).replace("{projectID}", projectID)
                    .replace("{imageID}", imageID).replace("{userID}", userID);

                // construct the payload
                var payload = "{ settingsID: " + settingsID + ", oldValue: " + oldValue + ", newValue: " + newValue + " }";

//			HINT: content-type "application/json" is default!
                // execute the http post request to the IRIS server
                $http.post(url, payload).success(function (data) {
                    // $log.debug("success on $http.get(" + url + ")");
//				$log.debug(data);
                    if (callbackSuccess) {
                        callbackSuccess(data);
                    }
                }).error(function (data, status, headers, config) {
                    // on error log the error
                    $log.error(status);
                    if (callbackError) {
                        callbackError(data, status, headers, config);
                    }
                })
            },

            // get the images with their progress for a project and a user (unfiltered!)
            fetchAllImages : function(projectID, userID, callbackSuccess, callbackError, offset, max) {
                var url = cytomineService.addKeys(imagesWithSettingsURL).replace("{projectID}",
                    projectID).replace("{userID}", userID);

                // add pagination parameters
                if (offset) {
                    // 0 offset is counted as missing parameter and causes loading of first page
                    url += "&offset=" + offset;
                }
                if (max) {
                    url += "&max=" + max;
                }

                // execute the get request to the server
                $http.get(url).success(function(data) {
                    if (callbackSuccess) {
                        callbackSuccess(data);
                    }
                }).error(function(data, status, headers, config) {
                    if (callbackError) {
                        callbackError(data, status);
                    }
                });
            },

            // set the project access for a given user
            setProjectAccess: function (projectID, userID, oldValue, newValue, settingsID, callbackSuccess,
                                      callbackError) {
                $log.debug("Posting project access settings change to IRIS: " + projectID + " - " + userID + " - " + newValue);

                // modify the parameters
                var url = cytomineService.addKeys(projectUsersAccessChangeURL).replace("{projectID}", projectID)
                    .replace("{userID}", userID);

                // construct the payload
                var payload = "{ settingsID: " + settingsID + ", oldValue: " + oldValue + ", newValue: " + newValue + " }";

//			HINT: content-type "application/json" is default!
                // execute the http post request to the IRIS server
                $http.post(url, payload).success(function (data) {
                    // $log.debug("success on $http.get(" + url + ")");
//				$log.debug(data);
                    if (callbackSuccess) {
                        callbackSuccess(data);
                    }
                }).error(function (data, status, headers, config) {
                    // on error log the error
                    $log.error(status);
                    if (callbackError) {
                        callbackError(data, status, headers, config);
                    }
                })
            },

            // set the project access for a given user
            triggerProjectSync: function (projectID, userID, imageIDs, newValue, settingsID, callbackSuccess,
                                        callbackError) {
                $log.debug("Posting project access settings change to IRIS: " + projectID + " - " + userID + " - " + imageIDs);

                // modify the parameters
                var url = cytomineService.addKeys(projectUsersSyncURL).replace("{projectID}", projectID)
                    .replace("{userID}", userID);

                if (imageIDs){
                    url += ("&images=" + imageIDs);
                }

//			HINT: content-type "application/json" is default!
                // execute the http post request to the IRIS server
                $http.post(url, null).success(function (data) {
                    // $log.debug("success on $http.get(" + url + ")");
//				$log.debug(data);
                    if (callbackSuccess) {
                        callbackSuccess(data);
                    }
                }).error(function (data, status, headers, config) {
                    // on error log the error
                    $log.error(status);
                    if (callbackError) {
                        callbackError(data, status, headers, config);
                    }
                })
            },
        }
    }]);