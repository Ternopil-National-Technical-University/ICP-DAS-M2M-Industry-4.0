import MenuBar.IGetPortAndSpeed;
import SerialDriver.*;
import jssc.SerialPort;
import jssc.SerialPortException;
import libraries.CommandHistory;
import libraries.I7000;

import javax.swing.*;
import javax.swing.plaf.TableHeaderUI;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

public class ContentPanel extends ContentPanelInit {
    private final CommandHistory commandHistory = new CommandHistory(100);
    private ArrayList<String []> devicesArrayList;
    private StringBuffer serialBuffer;
    private SerialDriver serialDriver;
    private Thread searchThread;
    private I7000 i7000;
    public ContentPanel(IGetPortAndSpeed portAndSpeed, ResourceBundle bundle) {
        super(bundle);
        devicesArrayList = new ArrayList<>();
        serialDriver = new SerialDriver();
        serialBuffer = new StringBuffer();
        searchThread = new Thread();
        i7000 = new I7000(false);
        onEnableWindow(false);
        setEnabledSearchButton(false);
        onInitCommand((ActionEvent event) -> {
            if (!serialDriver.isInit()) {
                serialDriver.initPort(portAndSpeed.getPortAndSpeed());
                SerialPort serialPort = SerialDriver.getSerialPort();
                try {
                    serialPort.addEventListener(new PortReader(serialPort, this::dataReadAction), SerialPort.MASK_RXCHAR);
                    JOptionPane.showMessageDialog(null, "Порт успішно ініціалізовано",
                            serialPort.getPortName(), JOptionPane.INFORMATION_MESSAGE);
                    setEnabledSearchButton(true);
                } catch (SerialPortException ignored) {
                }
            }
        });
        onSearchCommand((ActionEvent event) -> {
            if (searchThread.isAlive()) {
                searchThread.stop();
                setTextPercentLabel("Search stopped!!!");
            } else {
                searchThread = new Thread(this::SearchDevices);
                onEnableWindow(false);
                searchThread.start();
            }
        });
        onRenameCommand((ActionEvent event) -> {
            int index = getDevicesComboBox();
            String resultString1 = JOptionPane.showInputDialog(null,
                    "Поточна назва пристрою " + devicesArrayList.get(index)[1] + ".\nВведіть нову назву пристрою",
                    "Перейменувати модуль", JOptionPane.QUESTION_MESSAGE);
            if (resultString1 == null || resultString1.equals("")) return;
            String [] newData = new String[]{ devicesArrayList.get(index)[0], resultString1 };
            serialDriver.write(i7000.setModuleName(newData));
            new Thread(this::waitResponse).start();
            devicesArrayList.set(index, newData);
            updateData(devicesArrayList);
        });
        onChangeIdCommand((ActionEvent event) -> {});
        onSendCommand((ActionEvent event) -> {
            String command = getTextField();
            serialDriver.write(i7000.filter(command));
            commandHistory.addCommand(command);
            setTextField("");
            if (!waitResponse()) return;
            serialBuffer.append("\n");
            updateTextArea(serialBuffer.toString());
        });
        onChecksumCommand((ActionEvent event) -> {
            i7000.enabledCRC(checksumIsSelected());
        });
        onTextFieldKey(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP:
                        if (commandHistory.hasPreviousCommand()) {
                            setTextField(commandHistory.getPreviousCommand(getTextField()));
                        }
                        break;
                    case KeyEvent.VK_DOWN:
                        if (commandHistory.hasNextCommand()) {
                            setTextField(commandHistory.getNextCommand());
                        }
                        break;
                }
            }
        });
    }

    private boolean waitResponse() {
        final int timeOut = 100;
        serialBuffer.setLength(0);
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime < timeOut)) {
            if (serialBuffer.indexOf("\r") != -1) {
                return true;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        JOptionPane.showMessageDialog(null, "Час вийшов",
                "Помилка", JOptionPane.WARNING_MESSAGE);
        System.out.println(serialBuffer.toString());
        return false;
    }

    private void SearchDevices() {
        final int SearchPause = 500;
        serialBuffer.setLength(0);
        StringBuilder str = new StringBuilder("$");
        for (int i = getStart(), j = getStart(), s = getEnd() - getStart(); i < getEnd(); i++) {
            str.append(String.format("%02X", i));
            str.append("M");
            if (checksumIsSelected()) {
                str.append(i7000.getCRC(str.toString().toCharArray()));
            }
            str.append("\r");
            serialDriver.write(str.toString());
            str.setLength(1);
            setTextPercentLabel(100 * (i - j) / s + "%");
            try {
                Thread.sleep(SearchPause);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        setTextPercentLabel("100%");
        updateTextArea();
        searchThread.interrupt();
    }

    private void updateTextArea() {
        devicesArrayList.clear();
        if (serialBuffer.length() == 0) return;
        StringTokenizer tokenizer = new StringTokenizer(serialBuffer.toString(), "\r");
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (token.length() < 3) {
                continue;
            }
            String [] str = new String[2];
            str[0] = token.substring(1, 3);
            str[1] = token.substring(3, token.length() - (checksumIsSelected() ? 2 : 0));
            devicesArrayList.add(str);
        }
        updateData(devicesArrayList);
        onEnableWindow(true);
    }

    private void dataReadAction(String s) {
        serialBuffer.append(s);
    }
}
