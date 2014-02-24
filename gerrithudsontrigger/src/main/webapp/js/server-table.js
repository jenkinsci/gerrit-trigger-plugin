function serverTable() {
    // private funcs
    var ImgBtn = function(sName, fImage, oAttr) {
        var sAttr = '';
        if (oAttr != null) {
            for (var key in oAttr) {
                sAttr = sAttr + ' ' + key + '="' + oAttr[key] + '"';
            }
        }
        return '<button type="button" class="' + YAHOO.widget.DataTable.CLASS_BUTTON +
        '" name="' + sName + '"' + sAttr + '><img src="' + imagesURL + '/24x24/' + fImage + '" /></button>';
    }

    // Buttons
    var BtnServer = function(sStatus) {
        var imgFile = 'grey.png';
        var attr = {'disabled':'disabled'};
        if (sStatus == "up") {
            imgFile = 'blue.png';
            attr = null;
        } else if (sStatus == "down") {
            imgFile = 'red.png';
            attr = null;
        }
        return ImgBtn("server", imgFile, attr);
    }

    var BtnEdit = function() {
        return ImgBtn("edit", "gear.png", null);
    }

    var BtnRemove = function() {
        return ImgBtn("remove", "edit-delete.png", null);
    }

    // formatFrontEndLink
    var FrontEndLinkFormatter = function(elLiner, oRecord, oColumn, oData) {
        var serverName = oRecord.getData("name");
        var frontEndUrl = oRecord.getData("frontEndUrl");
        if (frontEndUrl != '') {
            elLiner.innerHTML = '<a href="' + frontEndUrl + '">' + serverName + '</a>';
        } else {
            elLiner.innerHTML = serverName;
        }
    };
    YAHOO.widget.DataTable.Formatter.formatFrontEndLink = FrontEndLinkFormatter;

    // controlServer
    var ControlServerFormatter = function(elLiner, oRecord, oColumn, oData, oDataTable) {
        var status = oRecord.getData("status");
        elLiner.innerHTML = BtnServer(status);
    };
    YAHOO.widget.DataTable.Formatter.controlServer = ControlServerFormatter;

    // editServer
    var EditServerFormatter = function(elLiner, oRecord, oColumn, oData, oDataTable) {
        var configUrl = oRecord.getData("serverUrl");
        elLiner.innerHTML = BtnEdit();
    }
    YAHOO.widget.DataTable.Formatter.editServer = EditServerFormatter;

    // removeServer
    var RemoveServerFormatter = function(elLiner, oRecord, oColumn, oData, oDataTable) {
        elLiner.innerHTML= BtnRemove();
    }
    YAHOO.widget.DataTable.Formatter.removeServer = RemoveServerFormatter;

    // DataSource
    var serverNames = new YAHOO.util.DataSource("serverStatuses");
    serverNames.responseType = YAHOO.util.DataSource.TYPE_JSON;
    serverNames.responseSchema = { resultsList: "servers", fields: ["name", "frontEndUrl", "serverUrl", "version", "status"] };

    // DataTable
    var dataTable = new YAHOO.widget.DataTable("server-list", columnDefs, serverNames);

    var connectionCallBack = {
        success: function(o) {
            serverNames.sendRequest('', {
                success: dataTable.onDataReturnInitializeTable,
                scope: dataTable});
        },
        scope: serverNames
    }

    // ClickButtonEvent
    dataTable.subscribe("buttonClickEvent", function(oArgs) {
        var elButton = oArgs.target;
        var oRecord = this.getRecord(elButton);

        // for BtnServer
        if (elButton.name == "server") {
            if (oRecord.getData("status") == "up") {
                YAHOO.log("Stop connection.");
                YAHOO.util.Connect.asyncRequest('GET', oRecord.getData("serverUrl") + "/sleep", connectionCallBack);
            } else if (oRecord.getData("status") == "down") {
                YAHOO.log("Start connection.");
                YAHOO.util.Connect.asyncRequest('GET', oRecord.getData("serverUrl") + "/wakeup", connectionCallBack);
            }
        }

        // for BtnEdit
        else if (elButton.name == "edit") {
            window.location.href = oRecord.getData("serverUrl");
        }

        // for BtnRemove
        else if (elButton.name = "remove") {
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
};
