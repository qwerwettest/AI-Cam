import QtQuick 2.15
import QtQuick.Window 2.15
import QtQuick.Controls 2.15

Window {
    id: root
    width: 900
    height: 700
    visible: true
    title: "Система мониторинга кабинета (YOLOv11)"
    color: "#121212" // Темная тема

    // Основной контейнер
    Column {
        anchors.fill: parent
        anchors.margins: 20
        spacing: 15

        // Шапка окна
        Text {
            text: "Live Поток: Камера ноутбука"
            color: "#aaaaaa"
            font.pixelSize: 18
            font.bold: true
        }

        // Область вывода видео
        Rectangle {
            width: parent.width
            height: parent.height - 120
            color: "black"
            radius: 5
            border.color: "#333333"
            clip: true

            Image {
                id: videoStream
                anchors.fill: parent
                fillMode: Image.PreserveAspectFit
                // Подключаем ImageProvider (нужно реализовать в C++)
                source: "image://camera/live"
                cache: false

                // Обновляем картинку по сигналу из C++
                Connections {
                    target: cameraWindowObject
                    function onFrameReady() {
                        // Меняем source, чтобы Image перерисовал кадр
                        videoStream.source = "image://camera/live?id=" + Math.random()
                    }
                }
            }

            // Индикатор детекции (точка в углу)
            Rectangle {
                anchors.top: parent.top
                anchors.right: parent.right
                anchors.margins: 15
                width: 15; height: 15
                radius: 7.5
                color: "red"
                opacity: cameraWindowObject.peopleCount > 0 ? 1.0 : 0.2

                SequentialAnimation on opacity {
                    running: cameraWindowObject.peopleCount > 0
                    loops: Animation.Infinite
                    NumberAnimation { from: 1.0; to: 0.2; duration: 500 }
                    NumberAnimation { from: 0.2; to: 1.0; duration: 500 }
                }
            }
        }

        // Нижняя панель со статусом
        Row {
            width: parent.width
            height: 60
            spacing: 20

            Rectangle {
                width: 250
                height: parent.height
                color: cameraWindowObject.peopleCount > 0 ? "#b71c1c" : "#1b5e20"
                radius: 8

                Text {
                    anchors.centerIn: parent
                    text: cameraWindowObject.peopleCount > 0
                          ? "ЗАНЯТО: " + cameraWindowObject.peopleCount + " чел."
                          : "СВОБОДНО"
                    color: "white"
                    font.pixelSize: 22
                    font.bold: true
                }
            }

            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: "Статус: Система активна (YOLOv11 ONNX)"
                color: "#666666"
                font.pixelSize: 14
            }
        }
    }
}
