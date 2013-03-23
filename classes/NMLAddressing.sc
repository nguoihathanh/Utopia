// uses dependancy to update interested parties
// do we need the serverAddr, or could this be handled via a separate object/addrBook?
// nice thing about ServerAddr is it could actually be on another machine
OSCitizen {
	var <name, <addr, <serverAddr, <online;

	*new {|name, addr, serverAddr, online = true|
		^super.newCopyArgs(name, addr, serverAddr, online);
	}

	online_ {|bool| if(bool != online, { online = bool; this.changed(\online) }) }
}

// can we make this so that if there is no me we have a centralised system?
// or everything via OSC, so that there's no distinction?
AddrBook {
	var dict, <me;

	*new { ^super.new.init }

	init { dict = IdentityDictionary.new; }

	send {|name ...msg| dict[name].addr.sendMsg(*msg) }

	sendAll {|...msg| dict.do({|citizen| citizen.addr.sendMsg(*msg); }); }

	add {|oscitizen| dict[oscitizen.name] = oscitizen; oscitizen.addDependant(this); this.changed(\add, oscitizen) }

	addMe {|meCitizen|
		meCitizen = meCitizen ?? {OSCitizen("whoami".unixCmdGetStdOut, NetAddr.localAddr, Server.default.addr)};
		this.add(meCitizen);
		me = meCitizen;
	}

	remove {|oscitizen| dict[oscitizen.name] = nil; oscitizen.removeDependant(this); this.changed(\remove, oscitizen) }

	update {|changed, what| this.changed(what, changed) }

	names { ^dict.keys }

	addrs { ^dict.values.collect({|citizen| citizen.addr }) }

	serverAddrs { ^dict.values.collect({|citizen| citizen.serverAddr }) }
}

// who's there?
CensusTaker {
	var <addrBook, period, me, inOSCFunc, outOSCFunc, lastResponses;

	*new { |addrBook, period = 1.0, me|
		addrBook = addrBook ?? { AddrBook.new };
		^super.newCopyArgs(addrBook, period).init(me);
	}

	// not totally sure about this me business...
	init {|argMe|
		if(argMe.notNil, {addrBook.addMe(argMe)}, { if(addrBook.me.isNil, {addrBook.addMe }) });
		me = addrBook.me;
		lastResponses = IdentityDictionary.new;
		this.makeOSCFuncs;
		this.takeCensus;
	}

	makeOSCFuncs {
		inOSCFunc = OSCFunc({|msg, time, addr|
			var name, serverAddr;
			name = msg[1];
			serverAddr = msg[2];
			addrBook[name].online = true;
			if(lastResponses[name].isNil, {
				addrBook.add(OSCitizen(name, addr, serverAddr));
			});
			lastResponses[name] = time;
		}, '/censusForm');

		outOSCFunc = OSCFunc({|msg, time, addr|
			addr.sendMsg('/censusForm', me.name, me.serverAddr);
		}, '/takeCensus');
	}

	takeCensus {
		var broadcastAddr;
		NetAddr.broadcastFlag = true;
		broadcastAddr = NetAddrMP("255.255.255.255", 57120 + (0..7));
		SystemClock.sched(0, {
			broadcastAddr.sendMsg('/takeCensus');
			if(period.notNil, { this.checkOnline; });
			period;
		});
	}

	// everybody still there?
	checkOnline {
		var now;
		now = Main.elapsedTime;
		lastResponses.keysValuesDo({|name, lastHeardFrom|
			if((now - lastHeardFrom) > (period * 2), { addrBook[name].online = false });
		});
	}

}