package fr.adh.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.jayfella.jme.worldpager.world.WorldSettings;
import com.jme3.app.ChaseCameraAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.network.Client;
import com.jme3.network.ClientStateListener;
import com.jme3.network.Network;
import com.jme3.niftygui.NiftyJmeDisplay;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;
import com.jme3.system.AppSettings;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;
import fr.adh.common.ChatMessage;
import fr.adh.common.LoginMessage;
import fr.adh.common.SpawnEntityMessage;
import fr.adh.common.WelcomeMessage;
import lombok.Getter;
import lombok.Setter;

public class AdhClient extends SimpleApplication implements ClientStateListener, ActionListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(AdhClient.class);

	private static final Vector3f SPAWN_POINT = new Vector3f(0f, 150f, 0f);
	private final Vector3f lightDir = new Vector3f(-0.1f, -0.1f, -0.1f);

	private AdhWorldState adhWorldState;
	private AdhWaterState adhWaterState;

	private Client client;
	private String playerName;

	@Getter
	private Nifty nifty;

	// Entities
	private final Node entitiesNode = new Node("Entities Node");
	private Map<Integer, Player> entities = new HashMap<>();

	// Temporarly
	private Player player;
	private final Vector3f walkDirection = new Vector3f(0, 0, 0);
	private final Vector3f viewDirection = new Vector3f(0, 0, 0);
	boolean leftStrafe = false, rightStrafe = false, forward = false, backward = false, leftRotate = false,
			rightRotate = false;
	@Setter
	private boolean inputEnable = true;
	private boolean isReady = false;

	private static AdhClient adhClient;

	public static final AdhClient getInstance() {
		if (adhClient == null) {
			adhClient = new AdhClient();
		}
		return adhClient;
	}

	public static void main(String[] args) {
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();

		LOGGER.info("Starting application...");

		AppSettings settings = new AppSettings(true);
		settings.setTitle("L'Aube des Heros - v1.0.0");
		settings.setResolution(800, 600);
		settings.setVSync(true);

		getInstance().setSettings(settings);
		getInstance().setShowSettings(false);
		getInstance().setDisplayFps(false);
		getInstance().setDisplayStatView(false);
		getInstance().start();
	}

	public final void sendMessage(String message) {
		if (client == null || !client.isConnected()) {
			LOGGER.warn("Unable to send message because client is not connected.");
			return;
		}
		client.send(new ChatMessage(playerName, message));
	}

	public void initLandscape(String playerName) {
		WorldSettings worldSettings = new WorldSettings();
		worldSettings.setWorldName("Adh World");
		worldSettings.setSeed(123);
		worldSettings.setNumThreads(3);

		adhWorldState = new AdhWorldState(worldSettings);
		stateManager.attach(adhWorldState);
		stateManager.attach(adhWorldState.getBulletAppState());
		rootNode.attachChild(adhWorldState.getWorldNode());

		adhWorldState.getBulletAppState().setDebugEnabled(true);

		adhWaterState = new AdhWaterState(lightDir);
		stateManager.attach(adhWaterState);

		player = new Player(assetManager, SPAWN_POINT, playerName);
		rootNode.attachChild(player.getModel());
		adhWorldState.getBulletAppState().getPhysicsSpace().add(player.getControl());
		attachCamera(player);
		// cam.setLocation(SPAWN_POINT);
		// flyCam.setEnabled(true);
		// flyCam.setMoveSpeed(50);
		// Temporarly
		DirectionalLight sun = new DirectionalLight();
		sun.setDirection(lightDir.normalize());
		sun.setColor(ColorRGBA.White.clone().multLocal(1f));
		rootNode.addLight(sun);
		AmbientLight al = new AmbientLight();
		al.setColor(ColorRGBA.White);
		rootNode.addLight(al);
//		adhWorldState = 
//		landscapeManager = new LandscapeManager(this);
//		landscapeManager.create(rootNode, guiFont, playerName);
		setupKeys();

		isReady = true;
	}

	@Override
	public void simpleInitApp() {
		getFlyByCamera().setEnabled(false);

		NiftyJmeDisplay niftyDisplay = new NiftyJmeDisplay(assetManager, inputManager, audioRenderer, guiViewPort);
		nifty = niftyDisplay.getNifty();
		nifty.fromXml("Interface/gui.xml", "login");

		guiViewPort.addProcessor(niftyDisplay);
		inputManager.setCursorVisible(true);
	}

	@Override
	public void simpleUpdate(float tpf) {
		super.simpleUpdate(tpf);
		if (!isReady) {
			return;
		}

		if (inputEnable) {
			// View player camera
			Vector3f camDir = cam.getDirection().mult(0.2f);
			Vector3f camLeft = cam.getLeft().mult(0.2f);
			camDir.y = 0;
			camLeft.y = 0;
			viewDirection.set(camDir);
			walkDirection.set(0, 0, 0);
			if (leftStrafe) {
				walkDirection.addLocal(camLeft);
			} else if (rightStrafe) {
				walkDirection.addLocal(camLeft.negate());
			}
			if (leftRotate) {
				viewDirection.addLocal(camLeft.mult(tpf));
			} else if (rightRotate) {
				viewDirection.addLocal(camLeft.mult(tpf).negate());
			}
			if (forward) {
				walkDirection.addLocal(camDir);
			} else if (backward) {
				walkDirection.addLocal(camDir.negate());
			}
			player.getControl().setWalkDirection(walkDirection);
			player.getControl().setViewDirection(viewDirection);
		}

		adhWorldState.setFollower(player.getLocation());
		adhWorldState.update(tpf);
		adhWaterState.update(tpf);
	}

	@Override
	public void simpleRender(RenderManager rm) {
		// TODO: add render code
	}

	@Override
	public void clientConnected(Client client) {
		LOGGER.info("Client [{}] is now connected on [{}] in version [{}].", client.getId(), client.getGameName(),
				client.getVersion());
	}

	@Override
	public void clientDisconnected(Client client, DisconnectInfo arg1) {
		LOGGER.info("Client now disconnecting.");
		stop();
	}

	public void createConnection(String login, String password) {
		playerName = login;
		try {
			client = Network.connectToServer("Aube des heros", 1, "localhost", 8080);
			client.addClientStateListener(this);
			client.addMessageListener(new ClientMessageListener(), ChatMessage.class, WelcomeMessage.class,
					SpawnEntityMessage.class);
			client.start();
			while (!client.isConnected()) {
				try {
					Thread.sleep(1000l);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			client.send(new LoginMessage(login, password));
		} catch (IOException ex) {
			LOGGER.error("Connection to server error", ex);
		}
	}

	@SuppressWarnings("unchecked")
	public static final <T extends ScreenController> T getScreenController(String screenName) {
		if (AdhClient.getInstance().getNifty() == null) {
			return null;
		}
		Screen startScreen = AdhClient.getInstance().getNifty().getScreen(screenName);
		if (startScreen == null) {
			return null;
		}
		return (T) startScreen.getScreenController();
	}

	@Override
	public void destroy() {
		if (client != null && client.isStarted()) {
			client.close();
		}
		super.destroy();
	}

	public void addEntity(int idEntity) {
		Player entity = new Player(assetManager, SPAWN_POINT);
		adhWorldState.getBulletAppState().getPhysicsSpace().add(entity.getControl());
		entitiesNode.attachChild(entity.getModel());
		entities.put(idEntity, entity);
	}

	public void removeEntity(int idEntity) {
		Player entity = entities.get(idEntity);
		if (entity == null) {
			return;
		}
		entitiesNode.detachChild(entity.getModel());
		adhWorldState.getBulletAppState().getPhysicsSpace().remove(entity.getControl());
		entities.remove(idEntity);
	}

	public void attachCamera(final Player player) {
		ChaseCameraAppState chaseCam = new ChaseCameraAppState();
		chaseCam.setTarget(player.getModel());
		stateManager.attach(chaseCam);
		chaseCam.setInvertHorizontalAxis(true);
		chaseCam.setInvertVerticalAxis(true);
		chaseCam.setZoomSpeed(0.5f);
		chaseCam.setMinVerticalRotation(-FastMath.HALF_PI);
		chaseCam.setRotationSpeed(3);
		chaseCam.setDefaultDistance(10);
		chaseCam.setMinDistance(0.01f);
		chaseCam.setMaxDistance(20f);
		chaseCam.setZoomSpeed(0.1f);
		chaseCam.setDefaultVerticalRotation(0.3f);
	}

	private void setupKeys() {
		inputManager.addMapping("Strafe Left", new KeyTrigger(KeyInput.KEY_Q), new KeyTrigger(KeyInput.KEY_Z));
		inputManager.addMapping("Strafe Right", new KeyTrigger(KeyInput.KEY_E), new KeyTrigger(KeyInput.KEY_X));
		inputManager.addMapping("Rotate Left", new KeyTrigger(KeyInput.KEY_A), new KeyTrigger(KeyInput.KEY_LEFT));
		inputManager.addMapping("Rotate Right", new KeyTrigger(KeyInput.KEY_D), new KeyTrigger(KeyInput.KEY_RIGHT));
		inputManager.addMapping("Walk Forward", new KeyTrigger(KeyInput.KEY_W), new KeyTrigger(KeyInput.KEY_UP));
		inputManager.addMapping("Walk Backward", new KeyTrigger(KeyInput.KEY_S), new KeyTrigger(KeyInput.KEY_DOWN));
		inputManager.addMapping("Jump", new KeyTrigger(KeyInput.KEY_SPACE));
		inputManager.addMapping("Shoot", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
		inputManager.addListener(this, "Strafe Left", "Strafe Right");
		inputManager.addListener(this, "Rotate Left", "Rotate Right");
		inputManager.addListener(this, "Walk Forward", "Walk Backward");
		inputManager.addListener(this, "Jump", "Shoot");
	}

	@Override
	public void onAction(String binding, boolean value, float tpf) {
		if (binding == null || !inputEnable) {
			LOGGER.warn("Input disabled!!!");
			return;
		}
		LOGGER.info("binding [{}] with value [{}]", binding, value);
		switch (binding) {
		case "Strafe Left":
			leftStrafe = value;
			break;
		case "Strafe Right":
			rightStrafe = value;
			break;
		case "Rotate Left":
			leftRotate = value;
			break;
		case "Rotate Right":
			rightRotate = value;
			break;
		case "Walk Forward":
			forward = value;
			break;
		case "Walk Backward":
			backward = value;
			break;
		case "Jump":
			player.getControl().jump();
			break;
		default:
			break;
		}
	}
}
