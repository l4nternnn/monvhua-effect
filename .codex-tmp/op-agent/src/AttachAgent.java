import com.sun.tools.attach.VirtualMachine;

public final class AttachAgent {
    private AttachAgent() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: AttachAgent <pid> <agentJar> <agentArgs>");
        }

        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try {
            vm.loadAgent(args[1], args[2]);
        } finally {
            vm.detach();
        }
    }
}
