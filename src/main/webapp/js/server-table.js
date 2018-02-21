/*
 *  The MIT License
 *
 *  Copyright 2014 rinrinne a.k.a. rin_ne. All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

/*global crumb*/

function serverTable() {
    'use strict';
    // private funcs
    var urlSysImg = function(iSize, fImage) {
        var rectImage = iSize + 'x' + iSize;
        return imagesURL + '/' + rectImage + '/' + fImage;
    };

    var urlImg = function(iSize, fImage) {
        var rectImage = iSize + 'x' + iSize;
        return pluginURL + '/images/' + rectImage + '/' + fImage;
    };

    var btnImgBase = function(bSystem, sName, fImage, oAttr) {
        var sAttr = '';
        var sImgURL = '';
        var key;
        if (oAttr !== null) {
            for (key in oAttr) {
                if (oAttr.hasOwnProperty(key)) {
                    sAttr = sAttr + ' ' + key + '="' + oAttr[key] + '"';
                }
            }
        }
        if (bSystem) {
            sImgURL = urlSysImg(24, fImage);
        } else {
            sImgURL = urlImg(24, fImage);
        }

        return '<button type="button" class="' + YAHOO.widget.DataTable.CLASS_BUTTON +
               '" name="' + sName + '"' + sAttr + '><img src="' + sImgURL + '" /></button>';
    };

    var btnSysImg = function(sName, fImage, oAttr) {
        return btnImgBase(true, sName, fImage, oAttr);
    };

    var btnImg = function(sName, fImage, oAttr) {
        return btnImgBase(false, sName, fImage, oAttr);
    };

    // Buttons
    var btnServer = function(sStatus) {
        var imgFile = 'grey.png';
        var attr = {'disabled': 'disabled'};
        if (sStatus === "up") {
            imgFile = 'blue.png';
            attr = null;
        } else if (sStatus === "down") {
            imgFile = 'red.png';
            attr = null;
        }
        return btnSysImg("server", imgFile, attr);
    };

    var btnEdit = function(bError, bWarning) {
        var imgFile = "gear.png";
        var sysImage = true;
        var btn;
        if (bError) {
            imgFile = "gear-error.png";
            sysImage = false;
        } else if (bWarning) {
            imgFile = "gear-warning.png";
            sysImage = false;
        }

        if (sysImage) {
            btn = btnSysImg("edit", imgFile, null);
        } else {
            btn = btnImg("edit", imgFile, null);
        }
        return btn;
    };

    var btnRemove = function() {
        return btnSysImg("remove", "edit-delete.png", null);
    };

    // formatFrontEndLink
    var formatterFrontEndLink = function(elLiner, oRecord, oColumn, oData) {
        var serverName = oRecord.getData("name");
        var frontEndUrl = oRecord.getData("frontEndUrl");
        if (frontEndUrl !== '') {
            elLiner.innerHTML = '<a href="' + frontEndUrl + '">' + serverName + '</a>';
        } else {
            elLiner.innerHTML = serverName;
        }
    };
    YAHOO.widget.DataTable.Formatter.formatFrontEndLink = formatterFrontEndLink;

    // controlServer
    var formatterControlServer = function(elLiner, oRecord, oColumn, oData, oDataTable) {
        var status = oRecord.getData("status");
        elLiner.innerHTML = btnServer(status);
    };
    YAHOO.widget.DataTable.Formatter.controlServer = formatterControlServer;

    // editServer
    var formatterEditServer = function(elLiner, oRecord, oColumn, oData, oDataTable) {
        var hasErrors = oRecord.getData("hasErrors");
        var hasWarnings = oRecord.getData("hasWarnings");
        elLiner.innerHTML = btnEdit(hasErrors, hasWarnings);
    };
    YAHOO.widget.DataTable.Formatter.editServer = formatterEditServer;

    // removeServer
    var formatterRemoveServer = function(elLiner, oRecord, oColumn, oData, oDataTable) {
        elLiner.innerHTML = btnRemove();
    };
    YAHOO.widget.DataTable.Formatter.removeServer = formatterRemoveServer;

    // DataSource
    var serverNames = new YAHOO.util.DataSource("serverStatuses");
    serverNames.responseType = YAHOO.util.DataSource.TYPE_JSON;
    serverNames.responseSchema = { resultsList: "servers",
            fields: ["name", "frontEndUrl", "serverUrl", "version", "status", "hasErrors", "hasWarnings"] };

    // DataTable
    var dataTable = new YAHOO.widget.DataTable("server-list", columnDefs, serverNames);

    var connectionCallBack = {
        success: function(o) {
            serverNames.sendRequest('', {
                success: dataTable.onDataReturnInitializeTable,
                scope: dataTable
            });
        },
        scope: serverNames
    };

    // ClickButtonEvent
    dataTable.subscribe("buttonClickEvent", function(oArgs) {
        var elButton = oArgs.target;
        var oRecord = this.getRecord(elButton);
        var crumbStr = "";
        if (crumb.fieldName !== null) {
            crumbStr = crumb.fieldName + "=" + crumb.value;
        }

        if (elButton.name === "server") {
            // for BtnServer
            if (oRecord.getData("status") === "up") {
                YAHOO.log("Stop connection.");
                elButton.firstElementChild.src = urlSysImg(24, "blue_anime.gif");
                YAHOO.util.Connect.asyncRequest('POST', oRecord.getData("serverUrl") + "/sleep", connectionCallBack, crumbStr);
            } else if (oRecord.getData("status") === "down") {
                YAHOO.log("Start connection.");
                elButton.firstElementChild.src = urlSysImg(24, "red_anime.gif");
                YAHOO.util.Connect.asyncRequest('POST', oRecord.getData("serverUrl") + "/wakeup", connectionCallBack, crumbStr);
            }
        } else if (elButton.name === "edit") {
            // for btnEdit
            window.location.href = oRecord.getData("serverUrl");
        } else if (elButton.name === "remove") {
            // for BtnRemove
            window.location.href = oRecord.getData("serverUrl") + '/remove';
        }
    });

    // Interval updater
    var callBack = {
        success: dataTable.onDataReturnInitializeTable,
        failure: function() {
            YAHOO.log("Datasource initialization failure", "error");
        },
        scope: dataTable
    };

    serverNames.setInterval(30000, null, callBack);
}
