var iris = angular.module("irisApp");

iris.constant("sessionURL", "api/session.json");
iris.constant("touchProjectURL",
		"api/session/{sessionID}/project/{projectID}/touch");
iris.constant("updateProjectURL",
		"api/session/{sessionID}/project/{projectID}");
iris.constant("touchImageURL",
		"api/session/{sessionID}/project/{projectID}/image/{imageID}/touch");
iris.constant("labelingProgressURL",
		"api/session/{sessionID}/project/{projectID}/image/{imageID}/progress");
iris.constant("touchAnnotationURL",
		"api/session/{sessionID}/project/{projectID}/image/{imageID}/annotation/{annID}/touch");

// A generic service for handling client sessions.
iris.factory("sessionService", [
"$http", "$log", "$location", "sessionURL",
"touchProjectURL", "updateProjectURL", "touchImageURL", "labelingProgressURL", "touchAnnotationURL", "sharedService",
"cytomineService",
	    function($http, $log, $location, sessionURL,
		touchProjectURL, updateProjectURL, touchImageURL, labelingProgressURL, touchAnnotationURL, sharedService,
		cytomineService) {

	return {
		// ###############################################################
		// SESSION MANAGEMENT

		// retrieve the session
		getSession : function() {
			return JSON.parse(localStorage.getItem("session"));
		},

		// set the session to the local storage
		setSession : function(session) {
			if (session) {
				localStorage.setItem("session", JSON.stringify(session));
			} else {
				localStorage.removeItem("session");
			}
		},

		// retrieve the current session for a user identified by public key
		// this will overwrite the existing local session in order to reflect
		// any updates performed by this user on another client
		fetchSession : function(callbackSuccess, callbackError) {
			var sessionService = this;
			var url = cytomineService.addKeys(sessionURL);
			// $log.debug(url)

			$http.get(url).success(function(data) {
				$log.debug("Successfully retrieved session. ID=" + data.id);

				// put the session to the local storage
				sessionService.setSession(data);

				if (callbackSuccess) {
					callbackSuccess(data);
				}
			}).error(
				function(data, status, header, config) {
					$log.error("Error retrieving session.");

					sessionService.setSession(undefined);
					
					if (callbackError) {
						callbackError(data, status);
					}
				})
		},

		// retrieve the currently active project
		getCurrentProject : function() {
			var sess = this.getSession();
			if (sess.currentProject != null) {
				// $log.debug("returning local IRIS project")
				return sess.currentProject;
			} else {
				$log.debug("No local project available.")
				return null;
			}
		},

		// set the currently active project
		setCurrentProject : function(project) {
			if (project == null) {
				var session = this.getSession();
				session.currentProject = null;
				this.setSession(session);
			} else {
				this.updateProject(project);
			}
		},

		// touches a project in the current session
		touchProject : function(cmProjectID, callbackSuccess, callbackError) {
			var sessionService = this;
			var session = sessionService.getSession();
			var url = cytomineService.addKeys(touchProjectURL).replace(
					"{sessionID}", session.id).replace("{projectID}",
					cmProjectID);

			$http.post(url, null).success(function(data) {
				// on success, update the project in the local storage
				session.currentProject = data;
				sessionService.setSession(session);
				if (callbackSuccess) {
					callbackSuccess(data)
				}
			}).error(function(data, status, header, config) {
				// on error, log the error message
				$log.error(data);

				if (callbackError) {
					callbackError(data, status)
				}
			});
		},

		// updates a project in the current session, payload is the IRIS project
		// instance
		updateProject : function(irisProject, callbackSuccess, callbackError) {
			var sessionService = this;
			var session = sessionService.getSession();

			var projectClass = irisProject['class']

			var url = cytomineService.addKeys(
					updateProjectURL + "?class=" + projectClass).replace(
					"{sessionID}", session.id).replace("{projectID}",
					irisProject.cmID);

			$http.put(url, irisProject).success(function(data) {
				// on success, update the project in the local storage
				session.currentProject = data;
				sessionService.setSession(session);

				$log.debug("successfully updated current project")

				if (callbackSuccess) {
					callbackSuccess(data)
				}
			}).error(
					function(data, status, header, config) {
						// on error, show the error message
						sharedService.addAlert(status
								+ ": failed updating project.", "danger");

						session.currentProject = null;

						if (callbackError) {
							callbackError(data, status)
						}
					});
		},

		// retrieve the currently active image
		getCurrentImage : function() {
			var sess = this.getSession();
			var cProj = sess.currentProject;
			if (cProj.currentImage != null) {
				$log.debug("Returning current IRIS image.")
				return cProj.currentImage;
			} else {
				$log.debug("Current IRIS image is null.")
				return null;
			}
		},

		// set the currently active image
		setCurrentImage : function(image) {
			var session = this.getSession();
			var cProj = session.currentProject;
			cProj.currentImage = image;
			this.setSession(session);
		},

		// touches an image in the current session
		touchImage : function(cmProjectID, cmImageID, callbackSuccess,
				callbackError) {
			var sessionService = this;
			var session = sessionService.getSession();

			var url = cytomineService.addKeys(touchImageURL).replace(
					"{sessionID}", session.id).replace("{projectID}",
					cmProjectID).replace("{imageID}", cmImageID);

			$http.post(url, null).success(function(data) {
				// on success, update the image in the local storage
				sessionService.setCurrentImage(data);

				if (callbackSuccess) {
					callbackSuccess(data)
				}
			}).error(function(data, status, header, config) {
				// on error, show the error message
				sharedService.addAlert(status + ": " + data, "danger");

				sessionService.setCurrentImage(null);
				
				if (callbackError) {
					callbackError(data, status)
				}
			});

		},
		
		// retrieve the currently active annotation
		getCurrentAnnotationID : function() {
			var sessionService = this;
			var cImg = sessionService.getCurrentImage();
			if (cImg != null && cImg.currentCmAnnotationID != null) {
				$log.debug("Returning current IRIS annotation")
				return cImg.currentCmAnnotationID;
			} else {
				$log.debug("Current IRIS annotation is null")
				return null;
			}
		},

		// set the currently active annotation
		setCurrentAnnotationID : function(annotationID) {
			var session = this.getSession();
			session.currentProject.currentImage.currentCmAnnotationID = annotationID;
			this.setSession(session);
		},
		
//		UNUSED AS OF IRIS v1.5
//		// touch the current annotation in this session
//		touchAnnotation : function(cmProjectID, cmImageID, cmAnnotationID, callbackSuccess,
//				callbackError) {
//			var sessionService = this;
//			var session = sessionService.getSession();
//
//			var url = cytomineService.addKeys(touchAnnotationURL).replace(
//					"{sessionID}", session.id).replace("{projectID}",
//					cmProjectID).replace("{imageID}", cmImageID)
//					.replace("{annID}", cmAnnotationID);
//
//			$http.post(url, null).success(function(data) {
//				// on success, update the image in the local storage
//				session.currentAnnotation = data;
//				sessionService.setSession(session);
//				if (callbackSuccess) {
//					callbackSuccess(data)
//				}
//			}).error(function(data, status, header, config) {
//				// on error, show the error message
//				sharedService.addAlert("Touching annotation failed! Status " + status + ".", "danger");
//				$log.error(data);
//
//				if (callbackError) {
//					callbackError(data, status)
//				}
//			});
//
//		},
		
		// gets the labeling progress (freshly computed) for an image in a project
		getLabelingProgress : function(cmProjectID, cmImageID, callbackSuccess,
				callbackError) {
			var sessionService = this;
			var session = sessionService.getSession();

			var url = cytomineService.addKeys(labelingProgressURL).replace(
					"{sessionID}", session.id).replace("{projectID}",
					cmProjectID).replace("{imageID}", cmImageID);

			$http.get(url).success(function(data) {
				if (callbackSuccess) {
					callbackSuccess(data)
				}
			}).error(function(data, status, header, config) {
				$log.error(data);
				if (callbackError) {
					callbackError(data, status)
				}
			});
		},

	// END SESSION MANAGEMENT
	// ###############################################################
	}
}]);