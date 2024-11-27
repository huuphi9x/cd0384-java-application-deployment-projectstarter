package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.AlarmStatus;
import com.udacity.catpoint.security.data.ArmingStatus;
import com.udacity.catpoint.security.data.SecurityRepository;
import com.udacity.catpoint.security.data.Sensor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static com.udacity.catpoint.security.data.AlarmStatus.ALARM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    @InjectMocks
    private SecurityService securityService;

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private StatusListener statusListener;

    @Mock
    private Sensor sensor;

    @BeforeEach
    void init() {
        securityService = new SecurityService(securityRepository, imageService);
    }

    @Test
    @DisplayName("Test add status listener")
    void addStatusListener_addsListener() {
        securityService.addStatusListener(statusListener);
        securityService.setAlarmStatus(ALARM);
        verify(statusListener, times(1)).notify(ALARM);
    }

    @Test
    @DisplayName("Test remove status listener")
    void removeStatusListener_removesListener() {
        securityService.addStatusListener(statusListener);
        securityService.removeStatusListener(statusListener);
        securityService.setAlarmStatus(ALARM);
        verify(statusListener, times(0)).notify(any(AlarmStatus.class));
    }

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = { "ARMED_AWAY", "ARMED_HOME" })
    @DisplayName("Test setting arming status sets alarm status correctly")
    void setArmingStatus_setsAlarmStatus(ArmingStatus armingStatus) {
        // Mock trạng thái vũ trang
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);

        // Thực hiện hành động cần kiểm tra
        securityService.setArmingStatus(armingStatus);

        // Kiểm tra rằng setAlarmStatus không được gọi
        verify(securityRepository, times(0)).setAlarmStatus(ALARM);
    }

    @Test
    void setArmingStatus_disarm_resetsAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = { "NO_ALARM", "PENDING_ALARM" })
    @DisplayName("Test sensor activation changes alarm status")
    void changeSensorActivationStatus_sensorActivated_setsAlarmStatus(AlarmStatus initialStatus) {
        when(securityRepository.getAlarmStatus()).thenReturn(initialStatus);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_AWAY);

        securityService.changeSensorActivationStatus(sensor, true);

        if (initialStatus == AlarmStatus.NO_ALARM) {
            verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
        } else if (initialStatus == AlarmStatus.PENDING_ALARM) {
            verify(securityRepository, times(1)).setAlarmStatus(ALARM);
        }
    }

    @Test
    void processImage_imageContainsCat_callsCatDetected() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(image, 50.0f)).thenReturn(true);
        securityService.processImage(image);
        verify(imageService).imageContainsCat(image, 50.0f);
    }

    @Test
    void setArmingStatus_armedAway_changesSensorStatusToInactive() {
        Sensor sensor = mock(Sensor.class);
        when(sensor.getActive()).thenReturn(true);

        Set<Sensor> sensors = new HashSet<>();
        sensors.add(sensor);

        when(securityRepository.getSensors()).thenReturn(sensors);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);

        // Mock giá trị trả về của getAlarmStatus() để tránh lỗi NullPointerException
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);

        // Thực hiện hành động thay đổi trạng thái vũ trang
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);

        // Kiểm tra rằng phương thức updateSensor và setActive đã được gọi đúng cách
        verify(securityRepository).updateSensor(sensor);
        verify(sensor).setActive(false);
    }


    @Test
    void handleSensorActivated_pendingAlarm_setsAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_AWAY);

        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(ALARM);
    }

    @Test
    void processImage_catDetected_setsAlarm() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(image, 50.0f)).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        securityService.processImage(image);
        verify(securityRepository, times(1)).setAlarmStatus(ALARM);
    }

    @Test
    void testNoActiveSensors_setsNoAlarm() {
        Sensor sensor1 = mock(Sensor.class);
        Sensor sensor2 = mock(Sensor.class);
        when(sensor1.getActive()).thenReturn(false);
        when(sensor2.getActive()).thenReturn(false);

        Set<Sensor> sensors = Set.of(sensor1, sensor2);
        when(securityRepository.getSensors()).thenReturn(sensors);

        BufferedImage image = mock(BufferedImage.class);
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(false);

        securityService.processImage(image);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    @DisplayName("Test setArmingStatus with ARMED_HOME and cat detection triggers ALARM status")
    void processImage_armedHome_andCatDetected_setsAlarmToALARM() {
        // Mock các đối tượng và phương thức cần thiết
        SecurityRepository securityRepository = mock(SecurityRepository.class);
        ImageService imageService = mock(ImageService.class);

        // Khởi tạo SecurityService
        SecurityService securityService = new SecurityService(securityRepository, imageService);

        // Mock trạng thái cảm biến và vũ trang
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);

        // Giả sử phát hiện con mèo
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);

        // Thiết lập trạng thái báo động khi có mèo
        securityService.processImage(mock(BufferedImage.class));

        // Kiểm tra rằng setAlarmStatus được gọi với AlarmStatus.ALARM
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    @DisplayName("Test setArmingStatus with ARMED_HOME and cat detection triggers ALARM status")
    void processImage_armedHome_andCatDetected_setsAlarmToALARM_noCat() {
        // Mock các đối tượng và phương thức cần thiết
        SecurityRepository securityRepository = mock(SecurityRepository.class);
        ImageService imageService = mock(ImageService.class);

        // Khởi tạo SecurityService
        SecurityService securityService = new SecurityService(securityRepository, imageService);

        // Giả sử phát hiện con mèo
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);

        // Thiết lập trạng thái báo động khi có mèo
        securityService.processImage(mock(BufferedImage.class));

        // Kiểm tra rằng setAlarmStatus được gọi với AlarmStatus.ALARM
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    @DisplayName("Test setArmingStatus with ARMED_HOME and hasCat true sets AlarmStatus to ALARM")
    void setArmingStatus_armedHomeAndHasCat_setsAlarmToALARM() {
        // Mock các đối tượng cần thiết
        SecurityRepository securityRepository = mock(SecurityRepository.class);
        ImageService imageService = mock(ImageService.class);

        // Tạo instance của SecurityService
        SecurityService securityService = new SecurityService(securityRepository, imageService);

        // Giả lập trạng thái hasCat là true
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));

        // Mock trạng thái vũ trang ban đầu là DISARMED
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);

        // Giả lập danh sách cảm biến để tránh lỗi getSensors()
        Set<Sensor> mockSensors = new HashSet<>();
        when(securityRepository.getSensors()).thenReturn(mockSensors);

        // Dọn sạch các lần gọi trước đó
        clearInvocations(securityRepository);

        // Thực thi phương thức setArmingStatus với trạng thái ARMED_HOME
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        // Kiểm tra rằng setAlarmStatus được gọi với AlarmStatus.ALARM
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    @DisplayName("Test handleSensorDeactivated sets AlarmStatus to NO_ALARM when AlarmStatus is PENDING_ALARM")
    void changeSensorActivationStatus_deactivateSensorWhenPendingAlarm_setsAlarmStatusToNoAlarm() {
        // Mock các đối tượng cần thiết
        Sensor mockSensor = mock(Sensor.class);
        when(mockSensor.getActive()).thenReturn(true); // Cảm biến ban đầu đang kích hoạt

        SecurityRepository securityRepository = mock(SecurityRepository.class);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM); // Báo động đang ở trạng thái PENDING_ALARM

        SecurityService securityService = new SecurityService(securityRepository, mock(ImageService.class));

        // Gọi phương thức với cảm biến đang hoạt động và chuyển sang không hoạt động
        securityService.changeSensorActivationStatus(mockSensor, false);

        // Kiểm tra rằng AlarmStatus được chuyển thành NO_ALARM
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);

        // Kiểm tra rằng cảm biến được cập nhật trạng thái
        verify(mockSensor).setActive(false);
        verify(securityRepository).updateSensor(mockSensor);
    }

    @Test
    @DisplayName("Test getAlarmStatus retrieves the correct AlarmStatus from SecurityRepository")
    void getAlarmStatus_returnsCorrectAlarmStatus() {
        // Mock SecurityRepository
        SecurityRepository mockRepository = mock(SecurityRepository.class);
        when(mockRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        // Tạo instance SecurityService với mock repository
        SecurityService securityService = new SecurityService(mockRepository, mock(ImageService.class));

        // Gọi phương thức getAlarmStatus
        AlarmStatus alarmStatus = securityService.getAlarmStatus();

        // Xác minh kết quả trả về
        assertEquals(AlarmStatus.PENDING_ALARM, alarmStatus);

        // Kiểm tra rằng phương thức getAlarmStatus() trong repository được gọi chính xác
        verify(mockRepository).getAlarmStatus();
    }

    @Test
    @DisplayName("Test addSensor calls SecurityRepository.addSensor with the correct sensor")
    void addSensor_callsRepositoryAddSensor() {
        // Mock SecurityRepository
        SecurityRepository mockRepository = mock(SecurityRepository.class);

        // Tạo instance SecurityService với mock repository
        SecurityService securityService = new SecurityService(mockRepository, mock(ImageService.class));

        // Tạo một mock sensor
        Sensor mockSensor = mock(Sensor.class);

        // Gọi phương thức addSensor
        securityService.addSensor(mockSensor);

        // Xác minh phương thức addSensor được gọi đúng
        verify(mockRepository).addSensor(mockSensor);
    }

    @Test
    @DisplayName("Test removeSensor calls SecurityRepository.removeSensor with the correct sensor")
    void removeSensor_callsRepositoryRemoveSensor() {
        // Mock SecurityRepository
        SecurityRepository mockRepository = mock(SecurityRepository.class);

        // Tạo instance SecurityService với mock repository
        SecurityService securityService = new SecurityService(mockRepository, mock(ImageService.class));

        // Tạo một mock sensor
        Sensor mockSensor = mock(Sensor.class);

        // Gọi phương thức removeSensor
        securityService.removeSensor(mockSensor);

        // Xác minh phương thức removeSensor được gọi đúng
        verify(mockRepository).removeSensor(mockSensor);
    }





}