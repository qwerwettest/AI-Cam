import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Window {
    id: root
    width: 450
    height: 600
    visible: true
    title: "Система поиска аудиторий"

    color: "#f0f2f5"

    // Перехватываем сигналы из C++ (например, ошибки)
    Connections {
        target: requestObject
        function onErrorOccurred(msg) {
            statusLabel.text = msg
            statusLabel.color = "red"
            statusLabel.visible = true
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 20
        spacing: 15

        Label {
            text: "Поиск свободных кабинетов"
            font.pixelSize: 22
            font.bold: true
            Layout.alignment: Qt.AlignHCenter
        }

        // --- Блок параметров поиска ---
        TextField {
            id: corpusField
            placeholderText: "Корпус (например, А)"
            Layout.fillWidth: true
        }

        TextField {
            id: timeField
            placeholderText: "Время (HH:mm, например 10:00)"
            Layout.fillWidth: true
        }

        RowLayout {
            Layout.fillWidth: true
            Label {
                text: "Длительность (мин):"
            }
            SpinBox {
                id: durationBox
                from: 15
                to: 300
                value: 90
                stepSize: 15
                Layout.fillWidth: true
            }
        }

        // Поле для вывода статусов или ошибок
        Label {
            id: statusLabel
            visible: false
            font.pixelSize: 14
            Layout.fillWidth: true
            wrapMode: Text.WordWrap
            horizontalAlignment: Text.AlignHCenter
        }

        // --- Кнопки управления ---
        RowLayout {
            Layout.fillWidth: true
            spacing: 10

            Button {
                text: "Найти"
                highlighted: true
                Layout.fillWidth: true
                onClicked: {
                    statusLabel.visible = false // прячем прошлую ошибку
                    // Передаем динамическую длительность из SpinBox
                    requestObject.findCabinet(corpusField.text, timeField.text, durationBox.value)
                }
            }

            Button {
                text: "Очистить бронь"
                Layout.fillWidth: true
                onClicked: {
                    requestObject.clearAllTemporary()
                    statusLabel.text = "Временные брони очищены"
                    statusLabel.color = "green"
                    statusLabel.visible = true
                }
            }
        }

        // --- Блок результата (показывается только если hasResult == true) ---
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 120
            color: "white"
            radius: 8
            border.color: "#ddd"
            visible: requestObject.hasResult

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 15
                spacing: 10

                // Выводим и название, и номер кабинета
                Text {
                    text: "Свободен: " + requestObject.lastFoundName + " (№ " + requestObject.lastFoundNumber + ")"
                    font.pixelSize: 18
                    font.bold: true
                    Layout.alignment: Qt.AlignHCenter
                }

                Button {
                    text: "Забронировать кабинет"
                    highlighted: true
                    Layout.alignment: Qt.AlignHCenter
                    onClicked: {
                        requestObject.bookCurrent(timeField.text, durationBox.value)
                        statusLabel.text = "Кабинет успешно забронирован!"
                        statusLabel.color = "green"
                        statusLabel.visible = true
                    }
                }
            }
        }

        Item { Layout.fillHeight: true } // Распорка, чтобы всё прижать к верху
    }
}
