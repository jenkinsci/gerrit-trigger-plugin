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
    var urlSysImgSvg = function(fImage) {
        return imagesURL + '/svgs/' + fImage;
    };

    var urlSysImg = function(iSize, fImage) {
        var rectImage = iSize + 'x' + iSize;
        return imagesURL + '/' + rectImage + '/' + fImage;
    };

    var urlImgPlugin = function(iSize, fImage) {
        var rectImage = iSize + 'x' + iSize;
        return pluginURL + '/images/' + rectImage + '/' + fImage;
    };

    var getAttributes = function(oAttr) {
        var sAttr = '';
        if (oAttr === null) {
            return sAttr;
        }

        var key = '';
        for (key in oAttr) {
            if (oAttr.hasOwnProperty(key)) {
                sAttr = sAttr + ' ' + key + '="' + oAttr[key] + '"';
            }
        }

        return sAttr;
    };

    var getImageURL = function(sSystem, fImage) {
        var url = '';
        if (sSystem === 'sysImgBasic') {
            url = urlSysImg(24, fImage);
        } else if (sSystem === 'sysImgSvg') {
            url = urlSysImgSvg(fImage);
        } else {
            url = urlImgPlugin(24, fImage);
        }
        return url;
    };

    var btnImgBase = function(sSystem, sName, fImage, oAttr) {
        var sAttr = getAttributes(oAttr);
        var sImgURL = getImageURL(sSystem, fImage);
        return '<button type="button" class="' + YAHOO.widget.DataTable.CLASS_BUTTON +
               '" name="' + sName + '"' + sAttr + '><img src="' + sImgURL + '" width="24" height="24"  /></button>';
    };

    var btnSysImgSvg = function(sName, fImage, oAttr) {
        return btnImgBase('sysImgSvg', sName, fImage, oAttr);
    };

    var btnSysImg = function(sName, fImage, oAttr) {
        return btnImgBase('sysImgBasic', sName, fImage, oAttr);
    };

    var btnImg = function(sName, fImage, oAttr) {
        return btnImgBase('pluginImg', sName, fImage, oAttr);
    };

    // Buttons
    var btnServer = function(sStatus) {
        var btn = null;
        if (sStatus === "up") {
            btn = btnSysImg("server", 'blue.png', null);
        } else if (sStatus === "down") {
            btn = btnSysImg("server", 'red.png', null);
        } else {
            btn = btnSysImg("server", "grey.png", {'disabled': 'disabled'});
        }
        return btn;
    };

    var btnEdit = function(bError, bWarning) {
        var btn = null;
        if (bError) {
            btn = btnImg("edit", "gear-error.png", null);
        } else if (bWarning) {
            btn = btnImg("edit", "gear-warning.png", null);
        } else {
            btn = btnSysImgSvg("edit", "gear.svg", null);
        }
        return btn;
    };

    var btnRemove = function() {
        return btnSysImgSvg("remove", "edit-delete.svg", null);
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
