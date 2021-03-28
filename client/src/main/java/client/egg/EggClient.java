package client.egg;

import client.PiClient;
import shared.GlobalConfig.CcFunction;

public class EggClient extends PiClient {
    private EggClient(String clientSettingsFile) throws Exception {
        super(clientSettingsFile);
        initChannel();
    }

    @Override
    protected void run() {
        System.out.println(invokeCC(CcFunction.GET_EGG));
        new SogetiLogoDrawer().printSogetiLogo();
    }

    public static void main(String[] args) throws Exception {
        new EggClient(args.length == 0 ? "client_settings.json" : args[0]).run();
    }
}
