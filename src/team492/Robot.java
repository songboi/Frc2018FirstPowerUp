/*
 * Copyright (c) 2018 Titan Robotics Club (http://www.titanrobotics.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package team492;

import org.opencv.core.Rect;

import com.ctre.phoenix.motorcontrol.FeedbackDevice;

import edu.wpi.cscore.CvSink;
import edu.wpi.cscore.CvSource;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.first.wpilibj.AnalogInput;
import edu.wpi.first.wpilibj.CameraServer;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj.DriverStation.MatchType;
import edu.wpi.first.wpilibj.I2C;
import edu.wpi.first.wpilibj.Relay;
import edu.wpi.first.wpilibj.SPI;
import edu.wpi.first.wpilibj.SerialPort;
import edu.wpi.first.wpilibj.Relay.Direction;
import edu.wpi.first.wpilibj.Relay.Value;
import frclib.FrcAHRSGyro;
import frclib.FrcCANTalon;
import frclib.FrcEmic2TextToSpeech;
import frclib.FrcI2cLEDPanel;
import frclib.FrcPneumatic;
import frclib.FrcRobotBase;
import frclib.FrcRobotBattery;
import hallib.HalDashboard;
import team492.PixyVision.TargetInfo;
import trclib.TrcDbgTrace;
import trclib.TrcDriveBase;
import trclib.TrcEmic2TextToSpeech.Voice;
import trclib.TrcGyro;
import trclib.TrcPidController;
import trclib.TrcPidController.PidCoefficients;
import trclib.TrcPidDrive;
import trclib.TrcRobot.RunMode;
import trclib.TrcRobotBattery;
import trclib.TrcUtil;

/**
 * The VM is configured to automatically run this class, and to call the
 * functions corresponding to each mode, as described in the TrcRobot
 * documentation. If you change the name of this class or the package after
 * creating this project, you must also update the manifest file in the
 * resource directory.
 */
public class Robot extends FrcRobotBase
{
    public static final String programName = "FirstPowerUp";
    private static final String moduleName = "Robot";

    public static final boolean USE_TRACELOG = true;
    public static final boolean USE_NAV_X = true;
    public static final boolean USE_USB_CAM = true;
    public static final boolean USE_GRIP_VISION = false;
    public static final boolean USE_PIXY_SPI = false;
    public static final boolean USE_PIXY_I2C = true;
    public static final boolean USE_TEXT_TO_SPEECH = true;
    public static final boolean USE_MESSAGE_BOARD = false;

    private static final boolean DEBUG_DRIVE_BASE = false;
    private static final boolean DEBUG_PID_DRIVE = false;
    private static final boolean DEBUG_GRIP_VISION = false;
    private static final boolean DEBUG_WINCH = false;
    private static final boolean DEBUG_ELEVATOR = true;
    private static final boolean DEBUG_CUBE_PICKUP = true;
    private static final boolean DEBUG_PIXY = true;
    private static final double DASHBOARD_UPDATE_INTERVAL = 0.1;
    private static final double SPEAK_PERIOD_SECONDS = 20.0; // Speaks once every this # of second.

    public DriverStation ds = DriverStation.getInstance();
    public HalDashboard dashboard = HalDashboard.getInstance();
    public TrcDbgTrace tracer = TrcDbgTrace.getGlobalTracer();

    public double targetHeading = 0.0;

    private double nextUpdateTime = TrcUtil.getCurrentTime();

    //
    // Sensors.
    //
    public TrcRobotBattery battery = null;
    public TrcGyro gyro = null;
    public AnalogInput pressureSensor = null;
    public AnalogInput frontSonarSensor = null;
    public AnalogInput leftSonarSensor = null;
    public AnalogInput rightSonarSensor = null;
    private double lastFrontSonarDistance = 0.0;
    private double lastLeftSonarDistance = 0.0;
    private double lastRightSonarDistance = 0.0;
    

    //
    // VisionTarget subsystem.
    //
    public GripVision gripVision = null;
    public PixyVision pixy = null;

    //
    // Miscellaneous subsystem.
    //
    public FrcI2cLEDPanel messageBoard = null;
    public FrcEmic2TextToSpeech tts = null;
    private double nextTimeToSpeakInSeconds = 0.0;  //0 means disabled, no need to speak;

    //
    // DriveBase subsystem.
    //
    public FrcCANTalon leftFrontWheel;
    public FrcCANTalon leftRearWheel;
    public FrcCANTalon rightFrontWheel;
    public FrcCANTalon rightRearWheel;
    public TrcDriveBase driveBase;

    public TrcPidController encoderXPidCtrl;
    public TrcPidController encoderYPidCtrl;
    public TrcPidController gyroTurnPidCtrl;
    public TrcPidDrive pidDrive;

    public TrcPidController sonarDrivePidCtrl;
    public TrcPidController visionTurnPidCtrl;
    public TrcPidDrive visionPidDrive;
    public TrcPidDrive sonarPidDrive;
    public TrcPidDrive visionPidTurn;

    //
    // Flippers subsystem.
    //
    public FrcPneumatic leftFlipper;
    public FrcPneumatic rightFlipper;
    
    //
    // Define our subsystems for Auto and TeleOp modes.
    //
    public Relay ringLightsPower;
    public CubePickup cubePickup;
    public Winch winch;
    public Elevator elevator;
    public CmdAutoCubePickup cmdAutoCubePickup;
    public CmdStrafeUntilCube cmdStrafeUntilCube;

    // FMS provided the following info:
    //  - event name
    //  - match type
    //  - match number
    //  - alliance
    //  - location
    //  - replay number???

    public String eventName = "Unknown";
    public MatchType matchType = MatchType.None;
    public int matchNumber = 0;
    public Alliance alliance = Alliance.Red;
    public int location = 1;
    public String gameSpecificMessage = null;

    public double driveTime;
    public double drivePower;
    public double driveDistance;
    public double drivePowerLimit;
    public double turnDegrees;
    public double frontSonarTarget;
    public double visionTurnTarget;
    public double tuneKp;
    public double tuneKi;
    public double tuneKd;
    public double tuneKf;

    /**
     * Constructor.
     */
    public Robot()
    {
        super(programName);
    }   //Robot

    /**
     * This function is run when the robot is first started up and should be used for any initialization code.
     */
    @Override
    public void robotInit()
    {
        //
        // Sensors.
        //
        battery = new FrcRobotBattery(RobotInfo.CANID_PDP);
        if (USE_NAV_X)
        {
            gyro = new FrcAHRSGyro("NavX", SPI.Port.kMXP);
        }
        pressureSensor = new AnalogInput(RobotInfo.AIN_PRESSURE_SENSOR);
        frontSonarSensor = new AnalogInput(RobotInfo.AIN_FRONT_SONAR_SENSOR);
        leftSonarSensor = new AnalogInput(RobotInfo.AIN_LEFT_SONAR_SENSOR);
        rightSonarSensor = new AnalogInput(RobotInfo.AIN_RIGHT_SONAR_SENSOR);

        //
        // VisionTarget subsystem.
        //
        if (USE_USB_CAM)
        {
            UsbCamera cam0 = CameraServer.getInstance().startAutomaticCapture("cam0", 0);
            cam0.setResolution(RobotInfo.USBCAM_WIDTH, RobotInfo.USBCAM_HEIGHT);
            cam0.setFPS(RobotInfo.USBCAM_FRAME_RATE);
            cam0.setBrightness(RobotInfo.USBCAM_BRIGHTNESS);
            if (USE_GRIP_VISION)
            {
                CvSink videoIn = CameraServer.getInstance().getVideo(cam0);
                CvSource videoOut = CameraServer.getInstance().putVideo(
                    "VisionTarget", RobotInfo.USBCAM_WIDTH, RobotInfo.USBCAM_HEIGHT);

                gripVision = new GripVision("GripVision", videoIn, videoOut);
            }
        }

        if (USE_PIXY_SPI)
        {
            pixy = new PixyVision(
                "PixyCam", this, RobotInfo.PIXY_POWER_CUBE_SIGNATURE, RobotInfo.PIXY_BRIGHTNESS,
                RobotInfo.PIXY_ORIENTATION, SPI.Port.kMXP);
        }
        else if(USE_PIXY_I2C)
        {
        	pixy = new PixyVision(
                    "PixyCam", this, RobotInfo.PIXY_POWER_CUBE_SIGNATURE, RobotInfo.PIXY_BRIGHTNESS,
                    RobotInfo.PIXY_ORIENTATION, I2C.Port.kMXP, RobotInfo.PIXYCAM_I2C_ADDRESS);
        }

        //
        // Miscellaneous subsystems.
        //
        if (USE_TEXT_TO_SPEECH)
        {
            tts = new FrcEmic2TextToSpeech("TextToSpeech", SerialPort.Port.kMXP, 9600);
            tts.setEnabled(true);
            tts.selectVoice(Voice.FrailFrank);
            tts.setVolume(0.72);
        }

        if (USE_MESSAGE_BOARD)
        {
            messageBoard = new FrcI2cLEDPanel("messageBoard", I2C.Port.kOnboard);
        }

        //
        // DriveBase subsystem.
        //
        leftFrontWheel = new FrcCANTalon("LeftFrontWheel", RobotInfo.CANID_LEFTFRONTWHEEL);
        leftRearWheel = new FrcCANTalon("LeftRearWheel", RobotInfo.CANID_LEFTREARWHEEL);
        rightFrontWheel = new FrcCANTalon("RightFrontWheel", RobotInfo.CANID_RIGHTFRONTWHEEL);
        rightRearWheel = new FrcCANTalon("RightRearWheel", RobotInfo.CANID_RIGHTREARWHEEL);

        //
        // Initialize each drive motor controller.
        //
        leftFrontWheel.setInverted(false);
        leftRearWheel.setInverted(false);
        rightFrontWheel.setInverted(true);
        rightRearWheel.setInverted(true);

        leftFrontWheel.motor.overrideLimitSwitchesEnable(false);
        leftRearWheel.motor.overrideLimitSwitchesEnable(false);
        rightFrontWheel.motor.overrideLimitSwitchesEnable(false);
        rightRearWheel.motor.overrideLimitSwitchesEnable(false);

        leftFrontWheel.setPositionSensorInverted(false);
        leftRearWheel.setPositionSensorInverted(false);
        rightFrontWheel.setPositionSensorInverted(false);
        rightRearWheel.setPositionSensorInverted(false);

        leftFrontWheel.setFeedbackDevice(FeedbackDevice.QuadEncoder);
        leftRearWheel.setFeedbackDevice(FeedbackDevice.QuadEncoder);
        rightFrontWheel.setFeedbackDevice(FeedbackDevice.QuadEncoder);
        rightRearWheel.setFeedbackDevice(FeedbackDevice.QuadEncoder);

        //
        // Initialize DriveBase subsystem.
        //
        driveBase = new TrcDriveBase(leftFrontWheel, leftRearWheel, rightFrontWheel, rightRearWheel, gyro);
        driveBase.setXPositionScale(RobotInfo.ENCODER_X_INCHES_PER_COUNT);
        driveBase.setYPositionScale(RobotInfo.ENCODER_Y_INCHES_PER_COUNT);

        //
        // Create PID controllers for DriveBase PID drive.
        //
        encoderXPidCtrl = new TrcPidController(
            "encoderXPidCtrl",
            new PidCoefficients(
                RobotInfo.ENCODER_X_KP, RobotInfo.ENCODER_X_KI, RobotInfo.ENCODER_X_KD, RobotInfo.ENCODER_X_KF),
            RobotInfo.ENCODER_X_TOLERANCE,
            driveBase::getXPosition);
        encoderYPidCtrl = new TrcPidController(
            "encoderYPidCtrl",
            new PidCoefficients(
                RobotInfo.ENCODER_Y_KP, RobotInfo.ENCODER_Y_KI, RobotInfo.ENCODER_Y_KD, RobotInfo.ENCODER_Y_KF),
            RobotInfo.ENCODER_Y_TOLERANCE,
            driveBase::getYPosition);
        gyroTurnPidCtrl = new TrcPidController(
            "gyroTurnPidCtrl",
            new PidCoefficients(
                RobotInfo.GYRO_TURN_KP, RobotInfo.GYRO_TURN_KI, RobotInfo.GYRO_TURN_KD, RobotInfo.GYRO_TURN_KF),
            RobotInfo.GYRO_TURN_TOLERANCE,
            driveBase::getHeading);
        gyroTurnPidCtrl.setAbsoluteSetPoint(true);
        pidDrive = new TrcPidDrive("pidDrive", driveBase, encoderXPidCtrl, encoderYPidCtrl, gyroTurnPidCtrl);
        pidDrive.setStallTimeout(RobotInfo.DRIVE_STALL_TIMEOUT);
        pidDrive.setMsgTracer(tracer);

        sonarDrivePidCtrl = new TrcPidController(
            "sonarDrivePidCtrl",
            new PidCoefficients(
                RobotInfo.SONAR_KP, RobotInfo.SONAR_KI, RobotInfo.SONAR_KD, RobotInfo.SONAR_KF),
            RobotInfo.SONAR_TOLERANCE,
            this::getFrontSonarDistance);
        sonarDrivePidCtrl.setAbsoluteSetPoint(true);
        sonarDrivePidCtrl.setInverted(true);
        visionTurnPidCtrl = new TrcPidController(
            "visionTurnPidCtrl",
            new PidCoefficients(
                RobotInfo.VISION_TURN_KP, RobotInfo.VISION_TURN_KI, RobotInfo.VISION_TURN_KD, RobotInfo.VISION_TURN_KF),
            RobotInfo.VISION_TURN_TOLERANCE,
            this::getPixyTargetAngle);
        visionTurnPidCtrl.setInverted(true);
        visionTurnPidCtrl.setAbsoluteSetPoint(true);
        visionPidDrive = new TrcPidDrive("visionPidDrive", driveBase, null, sonarDrivePidCtrl, visionTurnPidCtrl);
        visionPidDrive.setStallTimeout(RobotInfo.DRIVE_STALL_TIMEOUT);
        visionPidDrive.setMsgTracer(tracer);
        sonarPidDrive = new TrcPidDrive("sonarPidDrive", driveBase, null, sonarDrivePidCtrl, null);
        sonarPidDrive.setMsgTracer(tracer);
        visionPidTurn = new TrcPidDrive("visionPidTurn", driveBase, null, null, visionTurnPidCtrl);
        visionPidTurn.setMsgTracer(tracer);

        //
        // Initialize pneumatic flippers.
        //
        leftFlipper = new FrcPneumatic("leftFlipper", RobotInfo.CANID_PCM1, 
            RobotInfo.SOL_LEFT_FLIPPER_EXTEND, RobotInfo.SOL_LEFT_FLIPPER_RETRACT);
        rightFlipper =  new FrcPneumatic("rightFlipper", RobotInfo.CANID_PCM1, 
            RobotInfo.SOL_RIGHT_FLIPPER_EXTEND, RobotInfo.SOL_RIGHT_FLIPPER_RETRACT);

        //
        // Create other hardware subsystems.
        //
        ringLightsPower = new Relay(RobotInfo.RELAY_RINGLIGHT_POWER);
        ringLightsPower.setDirection(Direction.kForward);
        cubePickup = new CubePickup();
        winch = new Winch();
        elevator = new Elevator();
        cmdAutoCubePickup = new CmdAutoCubePickup(this);
        cmdStrafeUntilCube = new CmdStrafeUntilCube(this);
        
        //
        // Robot Modes.
        //
        setupRobotModes(new FrcTeleOp(this), new FrcAuto(this), new FrcTest(this), null);
    }   //robotInit

    public void robotStartMode(RunMode runMode)
    {
        if (tts != null)
        {
            if (runMode == RunMode.DISABLED_MODE)
            {
                // Robot is safe. Note: "disaibled" is not a typo. It forces the speech board to pronounce it correctly.
                tts.speak("Robot disaibled");
                nextTimeToSpeakInSeconds = 0.0;
            }
            else
            {
                // Robot is unsafe
                tts.speak("Robot enabled, stand clear");
                nextTimeToSpeakInSeconds = TrcUtil.getCurrentTime() + SPEAK_PERIOD_SECONDS;
            }
        }

        if (ds.isFMSAttached())
        {
            eventName = ds.getEventName();
            matchType = ds.getMatchType();
            matchNumber = ds.getMatchNumber();
        }
        alliance = ds.getAlliance();
        location = ds.getLocation();
        gameSpecificMessage = ds.getGameSpecificMessage();

        battery.setTaskEnabled(true);
        driveTime = HalDashboard.getNumber("DriveTime", 5.0);
        drivePower = HalDashboard.getNumber("DrivePower", 0.2);
        driveDistance = HalDashboard.getNumber("DriveDistance", 6.0);
        drivePowerLimit = HalDashboard.getNumber("DrivePowerLimit", 0.5);
        turnDegrees = HalDashboard.getNumber("TurnDegrees", 90.0);
        frontSonarTarget = HalDashboard.getNumber("FrontSonarTarget", 7.0);
        visionTurnTarget = HalDashboard.getNumber("VisionTurnTarget", 0.0);
        tuneKp = HalDashboard.getNumber("TuneKp", RobotInfo.GYRO_TURN_KP);
        tuneKi = HalDashboard.getNumber("TuneKi", RobotInfo.GYRO_TURN_KI);
        tuneKd = HalDashboard.getNumber("TuneKd", RobotInfo.GYRO_TURN_KD);
        tuneKf = HalDashboard.getNumber("TuneKf", 0.05);
    }   //robotStartMode

    public void robotStopMode(RunMode runMode)
    {
        driveBase.stop();
        battery.setTaskEnabled(false);
    }   //robotStopMode

    public void setVisionEnabled(boolean enabled)
    {
        if (gripVision != null)
        {
            ringLightsPower.set(enabled? Value.kOn: Value.kOff);
            gripVision.setVideoOutEnabled(enabled);
            gripVision.setEnabled(enabled);
            tracer.traceInfo("Vision", "Grip Vision is %s!", enabled? "enabled": "disabled");
        }

        if (pixy != null)
        {
            pixy.setEnabled(enabled);
            tracer.traceInfo("Vision", "Pixy is %s!", enabled? "enabled": "disabled");
        }
    }   //setVisionEnabled

    public void updateDashboard()
    {
        double currTime = TrcUtil.getCurrentTime();

        if (currTime >= nextUpdateTime)
        {
        	nextUpdateTime = currTime + DASHBOARD_UPDATE_INTERVAL;

            if (DEBUG_DRIVE_BASE)
            {
                //
                // DriveBase debug info.
                //
                dashboard.displayPrintf(8, "DriveBase: lf=%.0f, rf=%.0f, lr=%.0f, rr=%.0f",
                    leftFrontWheel.getPosition(), rightFrontWheel.getPosition(),
                    leftRearWheel.getPosition(), rightRearWheel.getPosition());
                dashboard.displayPrintf(9, "DriveBase: X=%.1f, Y=%.1f, Heading=%.1f",
                    driveBase.getXPosition(), driveBase.getYPosition(), driveBase.getHeading());

                if (DEBUG_PID_DRIVE)
                {
                    encoderXPidCtrl.displayPidInfo(10);
                    encoderYPidCtrl.displayPidInfo(12);
                    gyroTurnPidCtrl.displayPidInfo(14);
                }
                HalDashboard.putNumber("DriveBase.X", driveBase.getXPosition());
                HalDashboard.putNumber("DriveBase.Y", driveBase.getYPosition());
                HalDashboard.putNumber("DriveBase.Heading", driveBase.getHeading());
            }

            if (DEBUG_GRIP_VISION)
            {
                if (gripVision != null && gripVision.isEnabled())
                {
                    Rect[] targetRects = gripVision.getObjectRects();
                    tracer.traceInfo("GripVision", "Target is %s (%d)",
                        targetRects == null? "not found": "found", targetRects == null? 0: targetRects.length);
                    if (targetRects != null)
                    {
                        for (int i = 0; i < targetRects.length; i++)
                        {
                            dashboard.displayPrintf(8 + i, "x=%d, y=%d, width=%d, height=%d",
                                targetRects[i].x, targetRects[i].y, targetRects[i].width, targetRects[i].height);
                            tracer.traceInfo("TargetRect", "%02d: x=%d, y=%d, width=%d, height=%d",
                                i, targetRects[i].x, targetRects[i].y, targetRects[i].width, targetRects[i].height);
                        }
                    }
                }
            }

            if (DEBUG_WINCH)
            {
                dashboard.displayPrintf(8, "Winch: power=%.1f", winch.getPower());
            }

            if (DEBUG_ELEVATOR)
            {
                dashboard.displayPrintf(8, "Elevator: power=%.1f, position=%.1f(%.1f), limitSw=%b/%b",
                    elevator.getPower(), elevator.getPosition(), elevator.elevatorMotor.getPosition(),
                    elevator.elevatorMotor.isLowerLimitSwitchActive(),
                    elevator.elevatorMotor.isUpperLimitSwitchActive());
            }

            if (DEBUG_CUBE_PICKUP)
            {
                dashboard.displayPrintf(9, "CubePickup: power=%.1f, detected=%b",
                    cubePickup.getPower(), cubePickup.cubeDetected());
            }

            if (DEBUG_PIXY)
            {
                if (pixy != null && pixy.isEnabled())
                {
                    PixyVision.TargetInfo targetInfo = pixy.getTargetInfo();
                    if (targetInfo == null)
                    {
                        dashboard.displayPrintf(14, "Pixy: Target not found!");
                    }
                    else
                    {
                        dashboard.displayPrintf(14, "Pixy: x=%d, y=%d, width=%d, height=%d",
                            targetInfo.rect.x, targetInfo.rect.y, targetInfo.rect.width, targetInfo.rect.height);
                        dashboard.displayPrintf(15, "xDistance=%.1f, yDistance=%.1f, angle=%.1f, ultrasonic=%.1f",
                            targetInfo.xDistance, targetInfo.yDistance, targetInfo.angle, getFrontSonarDistance());
                    }
                }
            }
        }
    }   //updateDashboard

    public void announceSafety()
    {
        double currTime = TrcUtil.getCurrentTime();

        if (tts != null && nextTimeToSpeakInSeconds > 0.0 && currTime >= nextTimeToSpeakInSeconds)
        {
            tts.speak("Stand clear");
            nextTimeToSpeakInSeconds = currTime + SPEAK_PERIOD_SECONDS;
        }
    }   //announceSafety

    public void startTraceLog(String prefix)
    {
        String filePrefix = prefix != null? prefix: eventName + "_" + matchType.toString();
        if (prefix == null) filePrefix += String.format("%03d", matchNumber);
        tracer.openTraceLog("/home/lvuser/tracelog", filePrefix);
    }   //startTraceLog

    public void stopTraceLog()
    {
        tracer.closeTraceLog();
    }   //stopTraceLog

    public void traceStateInfo(double elapsedTime, String stateName)
    {
        tracer.traceInfo(moduleName, "[%5.3f] %10s: xPos=%6.2f,yPos=%6.2f,heading=%6.1f/%6.1f,volts=%.1f(%.1f)",
            elapsedTime, stateName, driveBase.getXPosition(), driveBase.getYPosition(), driveBase.getHeading(),
            targetHeading, battery.getVoltage(), battery.getLowestVoltage());
    }   //traceStateInfo

    //
    // Getters for sensor values.
    //

    public double getPressure()
    {
        return 50.0*pressureSensor.getVoltage() - 25.0;
    }   //getPressure

    public double getFrontSonarDistance()
    {
        double value = frontSonarSensor.getVoltage()/RobotInfo.SONAR_MILLIVOLTS_PER_INCH;

        if (value == 0.0)
        {
            value = lastFrontSonarDistance;
        }
        else
        {
            lastFrontSonarDistance = value;
        }

        return value;
    }   //getFrontSonarDistance

    public double getLeftSonarDistance()
    {
        double value = leftSonarSensor.getVoltage()/RobotInfo.SONAR_MILLIVOLTS_PER_INCH;

        if (value == 0.0)
        {
            value = lastLeftSonarDistance;
        }
        else
        {
            lastLeftSonarDistance = value;
        }

        return value;
    }   //getLeftSonarDistance

    public double getRightSonarDistance()
    {
        double value = rightSonarSensor.getVoltage()/RobotInfo.SONAR_MILLIVOLTS_PER_INCH;

        if (value == 0.0)
        {
            value = lastRightSonarDistance;
        }
        else
        {
            lastRightSonarDistance = value;
        }

        return value;
    }   //getRightSonarDistance

    public double getPixyTargetAngle()
    {
        TargetInfo targetInfo = pixy.getTargetInfo();
        return targetInfo != null? targetInfo.angle: 0.0;
    }

}   //class Robot
